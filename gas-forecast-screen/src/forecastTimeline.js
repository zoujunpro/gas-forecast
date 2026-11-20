// Separate line segments share one legend name and color so a model reads as
// one continuous curve, with a dashed segment only after the forecast boundary.
export function periodLabels(record, rows, history = false) {
  const cutoff = record.dataThrough
  return rows.map((row, i) => {
    if (!/^(回测\d+期|第\d+[天月旬])$/.test(row.label)) return row.label
    if (!cutoff) return row.label // Do not invent dates for records without an anchor.
    const anchor = new Date(cutoff + 'T00:00:00Z')
    const offset = history ? i - rows.length + 1 : i + 1
    if (record.unit === '月度') {
      anchor.setUTCDate(1); anchor.setUTCMonth(anchor.getUTCMonth() + offset)
      return anchor.toISOString().slice(0, 7)
    }
    if (record.unit === '旬度') {
      const n = anchor.getUTCFullYear() * 36 + anchor.getUTCMonth() * 3 + Math.min(2, Math.floor((anchor.getUTCDate() - 1) / 10)) + offset
      return `${Math.floor(n / 36)}-${String(Math.floor(n % 36 / 3) + 1).padStart(2, '0')}${['上旬', '中旬', '下旬'][n % 3]}`
    }
    anchor.setUTCDate(anchor.getUTCDate() + offset)
    return anchor.toISOString().slice(0, 10)
  })
}
export function buildForecastTimeline(record) {
  const history = record.backtest || []
  const future = record.rows || []
  const labels = [...periodLabels(record, history, true), ...periodLabels(record, future)]
  const palette = ['#346ddd', '#13a295', '#c38b35', '#9a78cc', '#bb5b85']
  const actual = [...history.map(r => r.actual ?? null), ...future.map(r => r.actual ?? null)]
  const series = []
  if (actual.some(v => v !== null)) series.push({
    name: '实际销量', type: 'line', data: actual, connectNulls: false,
    symbolSize: 5, itemStyle: { color: '#27364c' }, lineStyle: { color: '#27364c', width: 2.5 }, z: 4
  })
  const models = record.models?.length ? record.models : [{ name: '预测需求', forecast: future.map(r => r.value) }]
  for (const [i, model] of models.entries()) {
    const color = models.length > palette.length ? `hsl(${Math.round(i * 137.508 % 360)}, 62%, 43%)` : palette[i % palette.length]
    const historical = history.map(r => r[model.name] ?? null)
    const hasHistory = historical.some(v => v !== null)
    const common = { name: model.name, type: 'line', connectNulls: false, symbolSize: 5, itemStyle: { color } }
    if (hasHistory) series.push({ ...common, id: model.name + '-backtest',
      data: [...historical, ...future.map(() => null)], lineStyle: { color, width: 2, type: 'solid' } })
    // Repeat the final backtest value to anchor the dashed continuation exactly
    // at the end of the solid line. Never fabricate a missing backtest value.
    const anchor = history.length ? historical[historical.length - 1] : null
    const prefix = history.map((_, j) => j === history.length - 1 ? anchor : null)
    series.push({ ...common, id: model.name + '-forecast',
      data: [...prefix, ...future.map((_, j) => model.forecast[j] ?? null)],
      lineStyle: { color, width: 2, type: 'dashed' } })
  }
  if (series.length && history.length && future.length) {
    series[0].markLine = { symbol: 'none', silent: true,
      data: [{ xAxis: history.length - 0.5 }],
      lineStyle: { type: 'dashed', color: '#94a3b8', width: 1.5 },
      label: { formatter: '回测 / 预测', color: '#657b98', fontSize: 10 } }
    series[0].markArea = { silent: true, label: { show: false },
      itemStyle: { color: 'rgba(70,120,210,.045)' },
      data: [[{ xAxis: history.length - 0.5 }, { xAxis: labels.length - 0.5 }]] }
  }
  return { labels, series, historyCount: history.length, modelNames: models.map(m => m.name) }
}
