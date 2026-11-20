export function modelMetricsOption(record, width = 1200) {
  const models = record.models || []
  const metrics = [
    { key: 'mae', name: 'MAE', unit: record.volumeUnit, color: '#6e91c5', axis: 0 },
    { key: 'rmse', name: 'RMSE', unit: record.volumeUnit, color: '#26a285', axis: 0 },
    { key: 'wape', name: 'WAPE', unit: '%', color: '#c4a16b', axis: 1 }
  ]
  return {
    animationDuration: 300,
    color: metrics.map(m => m.color),
    legend: { top: 8, itemWidth: 13, itemHeight: 9, itemGap: 28,
      textStyle: { color: '#4d4d4d', fontSize: 12 } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, confine: true,
      renderMode: 'richText', padding: [12, 16],
      formatter: params => `${params[0]?.axisValueLabel || ''}\n` + params.map(p => {
        const m = metrics[p.seriesIndex]
        return `${m.name}：${p.value ?? '未记录'} ${m.unit}`
      }).join('\n') + '\n三项均越低越好；跨单位勿直接比较柱高'
    },
    grid: { left: 72, right: 70, top: 66, bottom: 54 },
    xAxis: { type: 'category', data: models.map(m => m.name),
      axisTick: { show: false }, axisLine: { lineStyle: { color: '#e2e6e9' } },
      axisLabel: { interval: 0, rotate: 0, color: '#686868', fontSize: 12,
        width: 80, overflow: 'truncate', formatter: name => name==='Bagging决策树'?'Bagging\n决策树':name } },
    yAxis: [
      { type: 'value', name: `MAE / RMSE（${record.volumeUnit}）`, min: 0,
        nameTextStyle: { color: '#686868', fontSize: 12 },
        axisLabel: { color: '#686868', fontSize: 12 },
        splitLine: { lineStyle: { color: '#eceff1', type: 'dashed' } } },
      { type: 'value', name: 'WAPE（%）', min: 0,
        nameTextStyle: { color: '#686868', fontSize: 12 },
        axisLabel: { color: '#686868', fontSize: 12, formatter: '{value}%' },
        splitLine: { show: false } }
    ],
    dataZoom: [{ type: 'inside', xAxisIndex: 0, start: 0, end: Math.min(100, (width<800?6:12) / Math.max(1, models.length) * 100), zoomOnMouseWheel: false, moveOnMouseWheel: true, moveOnMouseMove: true, preventDefaultMouseMove: true }],
    series: metrics.map(m => ({ name: m.name, type: 'bar', yAxisIndex: m.axis,
      barMaxWidth: 20, barGap: '20%', itemStyle: { color: m.color, borderRadius: [4, 4, 0, 0] },
      emphasis: { focus: 'series' }, data: models.map(model => model[m.key] ?? null) }))
  }
}
