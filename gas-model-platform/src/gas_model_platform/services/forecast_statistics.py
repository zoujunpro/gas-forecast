"""Deterministic statistics for one forecast, without resampling across grains."""
from collections import Counter
from datetime import date
from decimal import Decimal, InvalidOperation


def _number(value):
    try:
        number = Decimal(str(value))
        return number if number.is_finite() else None
    except (InvalidOperation, ValueError):
        return None


def _points(rows):
    counts = Counter(str(day) for day, _ in rows)
    # Duplicate periods are ambiguous, never sum them silently.
    return {str(day): _number(value) for day, value in rows
            if counts[str(day)] == 1 and _number(value) is not None}


def _rounded(value):
    return float(value.quantize(Decimal('0.01')))


def _comparison(forecast, baseline):
    if not forecast or not baseline:
        return {'status': 'UNAVAILABLE', 'reason': '缺少同粒度可比数据'}
    left, right = sum(forecast, Decimal(0)), sum(baseline, Decimal(0))
    delta = left - right
    return {'status': 'AVAILABLE' if right > 0 else 'INVALID_BASELINE',
            'forecastValue': _rounded(left), 'baselineValue': _rounded(right),
            'changeValue': _rounded(delta),
            'changePercent': _rounded(delta / right * 100) if right > 0 else None,
            'direction': 'UP' if delta > 0 else 'DOWN' if delta < 0 else 'FLAT',
            'reason': None if right > 0 else '比较基准不大于零，无法计算变化率'}


def calculate_forecast_statistics(points, backtest, history, grain, comparison=None):
    """Inputs are (ISO date, value) pairs; backtest actuals share forecast grain.

    Explicit comparison rows are aligned to forecast dates and never enter training.
    Raw history is known to be daily in the legacy execution contract. It is only used
    for daily YoY. Month/tenday comparisons use same-grain backtest actuals.
    """
    predicted = _points(points)
    dates = sorted(predicted)
    actual = _points(backtest)
    actual = {day: value for day, value in actual.items() if dates and day < dates[0]}
    label = {'day': '日', 'month': '月', 'tenday': '旬'}[grain]
    result = {'version': 1, 'grain': grain, 'unit': None,
              'count': len(dates), 'missingCount': len(points) - len(dates),
              'historicalComparison': {'status': 'UNAVAILABLE', 'reason': '缺少历史实际销量'},
              'yearOverYear': {'status': 'UNAVAILABLE', 'reason': '缺少同粒度去年同期数据'},
              'evidence': []}
    if not dates:
        result['evidence'] = ['暂无有效预测数据。']
        return result
    values = list(predicted.values())
    total = sum(values, Decimal(0))
    average = total / len(values)
    peak, trough = max(values), min(values)
    result.update(startDate=dates[0], endDate=dates[-1], total=_rounded(total),
                  periodAverage=_rounded(average), peakValue=_rounded(peak),
                  peakDates=[d for d in dates if predicted[d] == peak],
                  troughValue=_rounded(trough), troughDates=[d for d in dates if predicted[d] == trough],
                  endpointComparison=_comparison([predicted[dates[-1]]], [predicted[dates[0]]]))
    evidence = result['evidence']
    if actual:
        baseline = sum(actual.values(), Decimal(0)) / len(actual)
        historical_comparison = _comparison([average], [baseline])
        historical_comparison.update(basis='PERIOD_AVERAGE', source='BACKTEST_ACTUAL',
                          startDate=min(actual), endDate=max(actual), count=len(actual))
        result['historicalComparison'] = historical_comparison
        evidence.append(f"预测期{label}均{average:.2f}，回测实际期（{min(actual)}至{max(actual)}，{len(actual)}个{label}）均值{baseline:.2f}"
                        + (f"，变化{historical_comparison['changePercent']:+.2f}%。" if historical_comparison['changePercent'] is not None else '，基准不大于零，不计算变化率。'))
    else:
        evidence.append('暂无同粒度历史实际数据，不能判断较历史升降。')
    references = _points(comparison) if comparison is not None else (_points(history) if grain == 'day' else actual)
    if comparison is not None:
        # 快照同时保存历史区间和预测区间的同期点，供趋势图直接复用。
        result['lastYearPoints'] = [{'date': day, 'value': _rounded(value)} for day, value in sorted(references.items())]
        result['historyLastYearPoints'] = [item for item in result['lastYearPoints'] if item['date'] < dates[0]]
        result['forecastLastYearPoints'] = [item for item in result['lastYearPoints'] if item['date'] >= dates[0]]
    pairs = []
    for day in dates:
        current = date.fromisoformat(day)
        if comparison is not None:
            if day in references:
                pairs.append((predicted[day], references[day]))
            continue
        try:
            previous = current.replace(year=current.year - 1).isoformat()
        except ValueError:  # February 29 has no exact same-day counterpart.
            continue
        if previous in references:
            pairs.append((predicted[day], references[previous]))
    yoy = _comparison([p[0] for p in pairs], [p[1] for p in pairs])
    yoy.update(matchedCount=len(pairs), missingCount=len(dates) - len(pairs),
               coverage='COMPLETE' if len(pairs) == len(dates) else 'PARTIAL' if pairs else 'NONE')
    if pairs:
        yoy.update(baselinePeriodAverage=_rounded(sum((p[1] for p in pairs), Decimal(0)) / len(pairs)),
                   baselinePeakValue=_rounded(max(p[1] for p in pairs)))
    result['yearOverYear'] = yoy
    if pairs:
        evidence.append(f"去年同期匹配{len(pairs)}/{len(dates)}个{label}，可比预测合计{yoy['forecastValue']:.2f}，去年同期合计{yoy['baselineValue']:.2f}"
                        + (f"，同比变化{yoy['changePercent']:+.2f}%。" if yoy['changePercent'] is not None else '，基准不大于零，不计算同比。'))
    evidence.append(f"预测期{dates[0]}至{dates[-1]}，共{len(dates)}个{label}，合计{total:.2f}；峰值{peak:.2f}（{'、'.join(result['peakDates'])}），谷值{trough:.2f}（{'、'.join(result['troughDates'])}）。数值单位沿用原始销量单位。")
    if result['missingCount']:
        evidence.append(f"有{result['missingCount']}个预测点缺失、无效或日期重复，以上仅统计有效点。")
    return result
