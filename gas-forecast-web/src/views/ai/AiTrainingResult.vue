<template>
  <section class="training-card">
    <div class="title"><strong>训练信息与回测效果</strong><span>{{ statusText }}</span></div>
    <div class="basic-grid">
      <div><small>客户</small><b>{{ result.customerName || '-' }}</b></div>
      <div><small>区域 / 行业</small><b>{{ [result.regionName, result.industryName].filter(Boolean).join(' / ') || '-' }}</b></div>
      <div><small>最优模型</small><b>{{ result.bestModel || '-' }}</b></div>
      <div><small>模型版本</small><b>{{ result.modelVersion || '-' }}</b></div>
      <div><small>训练区间</small><b>{{ result.trainStartDate || '-' }} 至 {{ result.trainEndDate || '-' }}</b></div>
      <div><small>训练耗时</small><b>{{ durationText }}</b></div>
    </div>
    <div class="metric-grid">
      <div><small>MAPE</small><b>{{ percent(result.mape) }}</b></div>
      <div><small>WMAPE</small><b>{{ percent(result.wmape) }}</b></div>
      <div><small>SMAPE</small><b>{{ percent(result.smape) }}</b></div>
      <div><small>MAE</small><b>{{ number(result.mae) }}</b></div>
      <div><small>RMSE</small><b>{{ number(result.rmse) }}</b></div>
      <div><small>R²</small><b>{{ number(result.r2) }}</b></div>
    </div>
    <template v-if="result.backtestPoints?.length">
      <div class="chart-title"><strong>回测：实际值与预测值</strong><span>{{ result.backtestPoints.length }} 个数据点</span></div>
      <div ref="chartRef" class="chart" role="img" aria-label="训练回测实际值与预测值趋势图"></div>
    </template>
    <div v-else class="empty">该训练批次暂无回测明细，已展示现有训练信息和评估指标。</div>
    <div class="batch">训练批次：{{ result.batchNo }}</div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import echarts from '@/utils/echarts'

interface BacktestPoint { date: string; actualValue?: number; predictedValue?: number; absoluteError?: number; errorRate?: number }
interface TrainingResult {
  batchNo: string; status?: string; agentName?: string; customerName?: string; regionName?: string; industryName?: string
  trainStartDate?: string; trainEndDate?: string; bestModel?: string; modelVersion?: string; trainDurationSeconds?: number
  mape?: number; wmape?: number; smape?: number; mae?: number; rmse?: number; r2?: number
  createdAt?: string; updatedAt?: string; backtestPoints?: BacktestPoint[]
}
const props = defineProps<{ result: TrainingResult }>()
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const number = (value?: number) => value == null || !Number.isFinite(Number(value)) ? '-' : Number(value).toFixed(2)
const percent = (value?: number) => value == null || !Number.isFinite(Number(value)) ? '-' : `${Number(value).toFixed(2)}%`
const statusText = computed(() => ({ SUCCESS: '训练完成', RUNNING: '训练中', FAILED: '训练失败' }[String(props.result.status || '').toUpperCase()] || props.result.status || '状态未知'))
const durationText = computed(() => props.result.trainDurationSeconds == null ? '-' : `${Number(props.result.trainDurationSeconds).toFixed(1)} 秒`)
const renderChart = () => nextTick(() => {
  if (!chartRef.value || !props.result.backtestPoints?.length) return
  chart = echarts.getInstanceByDom(chartRef.value) || echarts.init(chartRef.value)
  const points = props.result.backtestPoints
  chart.setOption({
    animationDuration: 350,
    tooltip: { trigger: 'axis' },
    legend: { top: 4, data: ['实际值', '预测值', '绝对误差'], textStyle: { color: '#64748b' } },
    grid: { top: 40, right: 24, bottom: 48, left: 52 },
    xAxis: { type: 'category', data: points.map((point) => point.date), axisLabel: { color: '#64748b', rotate: 30, formatter: (value: string) => value.slice(5), interval: Math.max(0, Math.ceil(points.length / 6) - 1) }, axisTick: { show: false } },
    yAxis: { type: 'value', name: '用量', axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: '#edf1f6' } } },
    series: [
      { name: '实际值', type: 'line', smooth: true, symbolSize: 5, data: points.map((point) => point.actualValue ?? null), itemStyle: { color: '#2563eb' }, lineStyle: { width: 2.5 } },
      { name: '预测值', type: 'line', smooth: true, symbolSize: 5, data: points.map((point) => point.predictedValue ?? null), itemStyle: { color: '#b91c1c' }, lineStyle: { width: 2.5 } },
      { name: '绝对误差', type: 'bar', barMaxWidth: 14, data: points.map((point) => point.absoluteError ?? null), itemStyle: { color: 'rgba(245,158,11,.45)' } }
    ]
  }, true)
  chart.resize()
})
const resize = () => chart?.resize()
let resizeObserver: ResizeObserver | undefined
onMounted(() => {
  renderChart()
  window.addEventListener('resize', resize)
  if (chartRef.value) { resizeObserver = new ResizeObserver(resize); resizeObserver.observe(chartRef.value) }
})
onBeforeUnmount(() => { window.removeEventListener('resize', resize); resizeObserver?.disconnect(); chart?.dispose(); chart = null })
watch(() => props.result, renderChart, { deep: true })
</script>

<style scoped>
.training-card{margin-top:10px;padding:16px;border:1px solid #e1e7f0;border-radius:12px;background:#fff;min-width:0}.title,.chart-title{display:flex;justify-content:space-between;align-items:center;gap:12px}.title span{padding:3px 8px;border-radius:12px;background:#ecfdf5;color:#047857;font-size:11px}.basic-grid,.metric-grid{display:grid;gap:9px;margin-top:13px}.basic-grid{grid-template-columns:repeat(3,1fr)}.metric-grid{grid-template-columns:repeat(6,1fr)}.basic-grid>div,.metric-grid>div{padding:9px;background:#f7f9fc;border-radius:8px;min-width:0}.basic-grid small,.basic-grid b,.metric-grid small,.metric-grid b{display:block}.basic-grid small,.metric-grid small,.chart-title span,.batch{color:#94a3b8;font-size:11px}.basic-grid b,.metric-grid b{margin-top:4px;color:#334155;font-size:12px;overflow-wrap:anywhere}.chart-title{margin-top:18px;padding-top:14px;border-top:1px solid #edf1f6}.chart{width:100%;height:300px;margin-top:8px}.empty{margin-top:15px;padding:12px;border-radius:8px;background:#f8fafc;color:#64748b;font-size:12px}.batch{margin-top:10px}@media(max-width:760px){.basic-grid{grid-template-columns:1fr 1fr}.metric-grid{grid-template-columns:repeat(3,1fr)}}
</style>
