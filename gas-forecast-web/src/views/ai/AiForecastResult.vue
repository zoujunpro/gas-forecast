<template>
  <div class="result-card">
    <div class="result-title"><strong>预测结果</strong><span>{{ result.points.length }} 个周期</span></div>
    <div class="metrics">
      <div><small>客户</small><b>{{ result.customerName || '-' }}</b></div>
      <div><small>未来预测总量</small><b>{{ format(forecastTotal) }}</b></div>
      <div><small>预测趋势</small><b :class="trendClass">{{ trendText }}</b></div>
      <div><small>推荐模型</small><b>{{ result.training?.bestModel || '-' }}</b></div>
    </div>
    <section v-if="result.training" class="training-summary">
      <div class="sub-title"><strong>使用的训练结果</strong><span>训练批次：{{ result.training.batchNo }}</span></div>
      <div class="training-meta">
        <span>训练区间：{{ result.training.trainStartDate || '-' }} 至 {{ result.training.trainEndDate || '-' }}</span>
        <span>模型：{{ result.training.bestModel || '-' }}</span>
      </div>
      <div class="training-metrics">
        <div><small>MAPE</small><b>{{ formatPercent(result.training.mape) }}</b></div>
        <div><small>MAE</small><b>{{ formatNullable(result.training.mae) }}</b></div>
        <div><small>RMSE</small><b>{{ formatNullable(result.training.rmse) }}</b></div>
        <div><small>R²</small><b>{{ formatNullable(result.training.r2) }}</b></div>
      </div>
    </section>
    <div class="chart-tools">
      <span>{{ result.customerName || '客户' }}：未来{{ result.points.length }}个周期预测</span>
      <div><button type="button" title="缩小" :disabled="zoom <= 0.15" @click="changeZoom(-0.1)">−</button><b>{{ Math.round(zoom * 100) }}%</b><button type="button" title="放大" :disabled="zoom >= 3" @click="changeZoom(0.2)">＋</button><button type="button" @click="fitToView">全景</button></div>
    </div>
    <div class="legend"><span class="last-year-key">去年同期</span><span class="forecast-key">最新预测</span></div>
    <div ref="trendScroll" class="chart-scroll" @wheel.prevent="onWheel">
      <div class="chart-inner" :style="{ width: `${chartWidth}px` }">
        <svg class="chart bar-chart" :viewBox="`0 0 ${chartWidth} 210`" preserveAspectRatio="none" role="img" aria-label="未来用气预测柱状图">
          <line v-for="tick in yTicks" :key="tick.value" x1="44" :y1="tick.y" :x2="chartWidth - 18" :y2="tick.y" class="grid" />
          <text v-for="tick in yTicks" :key="`label-${tick.value}`" x="38" :y="tick.y + 4" class="axis-label" text-anchor="end">{{ tick.label }}</text>
          <line x1="44" y1="176" :x2="chartWidth - 18" y2="176" class="axis-line" />
          <g v-for="item in chartBars" :key="item.date">
            <rect v-if="item.referenceHeight > 0" class="bar reference-bar" :x="item.referenceX" :y="item.referenceY" :width="barWidth" :height="item.referenceHeight">
              <title>{{ item.date }} 去年同期：{{ formatNullable(item.referenceValue) }}</title>
            </rect>
            <rect class="bar forecast-bar" :x="item.forecastX" :y="item.forecastY" :width="barWidth" :height="item.forecastHeight">
              <title>{{ item.date }} 最新预测：{{ format(item.forecastValue) }}</title>
            </rect>
          </g>
        </svg>
        <div class="date-axis" :style="{ gridTemplateColumns: `repeat(${trendPoints.length}, ${dateColumnWidth}px)` }">
          <span v-for="(point, index) in trendPoints" :key="point.date">{{ shouldShowDate(index) ? point.date.slice(5) : '' }}</span>
        </div>
      </div>
    </div>
    <details><summary>查看每日明细</summary><div class="values">
      <div class="values-head"><span>日期</span><span>去年同期</span><span>最新预测</span></div>
      <div v-for="point in result.points" :key="point.date" class="values-row"><span>{{ point.date }}</span><span>{{ formatNullable(lastYearValue(point.date)) }}</span><b>{{ format(point.value) }}</b></div>
    </div></details>
    <div class="batch">预测批次：{{ result.batchNo }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

interface ForecastPoint { date: string; value: number }
interface BacktestPoint { date: string; actualValue: number; predictedValue: number }
interface TrainingResult { batchNo: string; trainStartDate?: string; trainEndDate?: string; bestModel?: string; mape?: number; wmape?: number; smape?: number; mae?: number; rmse?: number; r2?: number }
interface ForecastResult { batchNo: string; customerName?: string; average: number; minimum: number; maximum: number; training?: TrainingResult; points: ForecastPoint[]; historyPoints?: ForecastPoint[]; lastYearPoints?: ForecastPoint[]; backtestPoints?: BacktestPoint[] }
const props = defineProps<{ result: ForecastResult }>()
const trendScroll = ref<HTMLElement>()
const format = (value: number) => Number(value).toFixed(2)
const formatNullable = (value?: number) => value == null || !Number.isFinite(Number(value)) ? '-' : format(Number(value))
const formatPercent = (value?: number) => value == null || !Number.isFinite(Number(value)) ? '-' : `${format(Number(value))}%`
const forecastTotal = computed(() => props.result.points.reduce((sum, point) => sum + Number(point.value || 0), 0))
const lastYearValueByDate = computed(() => new Map((props.result.lastYearPoints || []).map((point) => [point.date, point.value])))
const lastYearValue = (date: string) => lastYearValueByDate.value.get(date)
const trendPercent = computed(() => {
  if (props.result.points.length < 2) return 0
  const first = Number(props.result.points[0].value)
  const last = Number(props.result.points[props.result.points.length - 1].value)
  return first === 0 ? 0 : ((last - first) / Math.abs(first)) * 100
})
const trendText = computed(() => Math.abs(trendPercent.value) < 0.1 ? '基本平稳' : `${trendPercent.value > 0 ? '上升' : '下降'} ${Math.abs(trendPercent.value).toFixed(1)}%`)
const trendClass = computed(() => trendPercent.value > 0.1 ? 'trend-up' : trendPercent.value < -0.1 ? 'trend-down' : '')
const zoom = ref(1)
const changeZoom = (delta: number) => { zoom.value = Math.min(3, Math.max(0.15, Number((zoom.value + delta).toFixed(2)))) }
const onWheel = (event: WheelEvent) => changeZoom(event.deltaY < 0 ? 0.1 : -0.1)
const dateColumnWidth = computed(() => 68 * zoom.value)
const trendPoints = computed(() => props.result.points.map((item) => ({ ...item, series: 'forecast' })))
const chartWidth = computed(() => Math.max(420, trendPoints.value.length * 68 * zoom.value))
const chartValues = computed(() => [...trendPoints.value, ...(props.result.lastYearPoints || [])]
  .map((item) => Number(item.value))
  .filter(Number.isFinite))
const chartMax = computed(() => Math.max(...chartValues.value, 1))
const yTicks = computed(() => {
  const max = chartMax.value
  return [0, 0.25, 0.5, 0.75, 1].map((ratio) => {
    const value = max * ratio
    return {
      value,
      label: value >= 1000 ? `${Math.round(value / 100) / 10}k` : String(Math.round(value)),
      y: 176 - ratio * 146
    }
  }).reverse()
})
const barWidth = computed(() => Math.max(8, Math.min(22, dateColumnWidth.value * 0.22)))
const panoramaLabelStep = computed(() => Math.max(1, Math.ceil(trendPoints.value.length / 12)))
const shouldShowDate = (index: number) => index === 0 || index === trendPoints.value.length - 1 || index % panoramaLabelStep.value === 0
const barGeometry = (value?: number) => {
  const normalized = value == null || !Number.isFinite(Number(value)) ? 0 : Math.max(0, Number(value))
  const height = chartMax.value <= 0 ? 0 : (normalized / chartMax.value) * 146
  return { y: 176 - height, height }
}
const chartBars = computed(() => trendPoints.value.map((item, index) => {
  const referenceValue = lastYearValue(item.date)
  const reference = barGeometry(referenceValue)
  const forecast = barGeometry(item.value)
  const centerX = 44 + dateColumnWidth.value * index + dateColumnWidth.value / 2
  const gap = Math.max(3, barWidth.value * 0.25)
  return {
    date: item.date,
    referenceValue,
    forecastValue: item.value,
    referenceX: centerX - barWidth.value - gap / 2,
    forecastX: centerX + gap / 2,
    referenceY: reference.y,
    referenceHeight: reference.height,
    forecastY: forecast.y,
    forecastHeight: forecast.height
  }
}))
const fitToView = () => nextTick(() => {
  const container = trendScroll.value
  const count = Math.max(trendPoints.value.length, 1)
  if (!container) return
  zoom.value = Math.min(1, Math.max(0.15, Number(((container.clientWidth - 12) / (count * 68)).toFixed(2))))
  container.scrollLeft = 0
})
let resizeObserver: ResizeObserver | undefined
onMounted(() => {
  fitToView()
  if (trendScroll.value) {
    resizeObserver = new ResizeObserver(fitToView)
    resizeObserver.observe(trendScroll.value)
  }
})
onBeforeUnmount(() => resizeObserver?.disconnect())
watch(() => props.result.batchNo, fitToView)
</script>

<style scoped>
.result-card{margin-top:10px;padding:16px;border:1px solid #e1e7f0;border-radius:12px;background:#fff;min-width:0;max-width:100%}.result-title{display:flex;justify-content:space-between;align-items:center}.result-title span,.batch{font-size:11px;color:#94a3b8}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:14px 0}.metrics>div{padding:10px;background:#f7f9fc;border-radius:8px}.metrics small,.metrics b{display:block}.metrics small{color:#94a3b8}.metrics b{margin-top:4px;color:#1e3a5f}.chart-tools{display:flex;align-items:center;justify-content:space-between;margin:4px 0 8px;font-size:12px;color:#64748b}.chart-tools>div{display:flex;align-items:center;gap:5px}.chart-tools button{height:25px;min-width:27px;padding:0 7px;border:1px solid #d9e1ec;border-radius:5px;background:#fff;color:#475569;cursor:pointer}.chart-tools button:hover:not(:disabled){border-color:#4776ef;color:#4776ef}.chart-tools button:disabled{opacity:.4;cursor:not-allowed}.chart-tools b{min-width:42px;text-align:center;font-size:11px}.chart-scroll{width:100%;overflow-x:auto;overflow-y:hidden;padding-bottom:8px;scrollbar-gutter:stable}.chart-inner{min-width:420px}.chart{display:block;width:100%;height:210px}.grid{stroke:#edf1f6;stroke-width:1}.axis-line{stroke:#94a3b8;stroke-width:1}.axis-label{fill:#64748b;font-size:10px}.bar{rx:1.5;shape-rendering:geometricPrecision}.reference-bar{fill:#9ca3af}.forecast-bar{fill:#b91c1c}.date-axis{display:grid;color:#64748b;font-size:10px}.date-axis span{width:68px;text-align:center;white-space:nowrap;transform:rotate(-35deg);transform-origin:50% 0}.date-axis{height:50px;padding-top:5px}.chart-scroll::-webkit-scrollbar{height:8px}.chart-scroll::-webkit-scrollbar-thumb{background:#cbd5e1;border-radius:8px}.chart-scroll::-webkit-scrollbar-track{background:#f1f5f9;border-radius:8px}.result-card details{margin-top:12px}.result-card summary{cursor:pointer;color:#4776ef;font-size:12px}.values{max-height:220px;overflow:auto;margin-top:8px;border-top:1px solid #edf1f6}.values>div{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;padding:7px 3px;border-bottom:1px solid #f1f4f8;font-size:12px}.values-head{position:sticky;top:0;z-index:1;background:#f8fafc;color:#64748b;font-weight:600}.values-row span:nth-child(2),.values-row b{text-align:right}.batch{margin-top:10px}@media(max-width:760px){.metrics{grid-template-columns:1fr 1fr 1fr}}
.date-axis span{width:100%}
.legend{display:flex;gap:16px;margin:0 0 4px;font-size:11px;color:#64748b}.legend span:before{content:'';display:inline-block;width:14px;height:8px;margin-right:5px;vertical-align:-1px;border-radius:1px}.history-key:before,.actual-key:before{background:#16a34a}.last-year-key:before{background:#9ca3af}.forecast-key:before{background:#b91c1c}.backtest-key:before{background:#f59e0b}.backtest-section{margin-top:18px;padding-top:14px;border-top:1px solid #edf1f6}.backtest-section .chart-tools small{color:#94a3b8}.chart-scroll{cursor:ew-resize}
.metrics{grid-template-columns:repeat(4,1fr)}.trend-up{color:#ef4444!important}.trend-down{color:#16a34a!important}.training-summary{margin:4px 0 14px;padding:12px;border:1px solid #e8edf5;border-radius:10px;background:#fbfcff}.sub-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.sub-title span,.training-meta{font-size:11px;color:#94a3b8}.training-meta{display:flex;gap:18px;margin-top:6px}.training-metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:10px}.training-metrics>div{padding:8px;background:#fff;border-radius:7px}.training-metrics small,.training-metrics b{display:block}.training-metrics small{color:#94a3b8}.training-metrics b{margin-top:3px;color:#334155}@media(max-width:760px){.metrics,.training-metrics{grid-template-columns:1fr 1fr}.training-meta{flex-direction:column;gap:3px}}
</style>
