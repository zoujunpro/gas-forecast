<template>
  <div class="forecast-comparison-chart-wrap">
    <button
      class="chart-download-button"
      type="button"
      title="下载图表"
      aria-label="下载图表"
      @click="downloadChart"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3v11m0 0 4-4m-4 4-4-4M5 16v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3" />
      </svg>
    </button>
    <button
      class="chart-reset-button"
      type="button"
      title="重置缩放"
      aria-label="重置缩放"
      @click="resetZoom"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4 12a8 8 0 1 0 2.35-5.65M4 5v4h4" />
      </svg>
    </button>
    <div ref="root" class="forecast-comparison-chart" role="img" :aria-label="`${referenceName}、${forecastName}${extraSeriesName ? `与${extraSeriesName}` : ''}趋势图`" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import echarts from '@/utils/echarts'

const props = withDefaults(defineProps<{
  dates: string[]
  referenceValues?: Array<number | null | undefined>
  forecastValues: Array<number | null | undefined>
  chartType?: 'bar' | 'line'
  referenceName?: string
  forecastName?: string
  referenceColor?: string
  forecastColor?: string
  extraSeriesValues?: Array<number | null | undefined>
  extraSeriesName?: string
  extraSeriesColor?: string
  forecastStartDate?: string
  legendAlign?: 'left' | 'center'
  yAxisName?: string
  forecastAreaColor?: string
}>(), {
  referenceValues: () => [],
  chartType: 'bar',
  referenceName: '去年同期',
  forecastName: '最新预测',
  referenceColor: '#9ca3af',
  forecastColor: '#b91c1c',
  extraSeriesValues: () => [],
  extraSeriesName: '',
  extraSeriesColor: '#f59e0b',
  legendAlign: 'center',
  yAxisName: '',
  forecastAreaColor: 'rgba(37,99,235,.055)'
})

const root = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const downloadChart = () => {
  if (!chart) return
  const link = document.createElement('a')
  link.href = chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#ffffff' })
  link.download = `${props.referenceName}-${props.forecastName}趋势图.png`
  document.body.appendChild(link)
  link.click()
  link.remove()
}
const resetZoom = () => chart?.dispatchAction({ type: 'dataZoom', start: 0, end: 100 })
const formatAxisDate = (value: unknown) => {
  const date = String(value || '')
  const matched = date.match(/\d{4}-(\d{2})-(\d{2})/)
  return matched ? `${matched[1]}-${matched[2]}` : date
}
const axisLabelInterval = (index: number) => {
  const count = props.dates.length
  if (count <= 8) return true
  const step = Math.ceil((count - 1) / 7)
  return index === 0 || index === count - 1 || index % step === 0
}
const render = () => nextTick(() => {
  if (!root.value) return
  chart = echarts.getInstanceByDom(root.value) || echarts.init(root.value)
  const isBar = props.chartType === 'bar'
  chart.setOption({
    animationDuration: 350,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: isBar ? 'shadow' : 'line', lineStyle: { color: '#93c5fd', width: 1, type: 'dashed' }, shadowStyle: { color: 'rgba(37,99,235,.05)' } },
      backgroundColor: 'rgba(255,255,255,0.98)',
      borderColor: '#E2E8F0',
      borderRadius: 8,
      padding: [8, 10],
      extraCssText: 'box-shadow: 0 8px 20px rgba(15,23,42,.10);',
      textStyle: { color: '#0F172A', fontSize: 12 },
      formatter: (items: any[]) => {
        const date = items?.[0]?.axisValue || items?.[0]?.axisValueLabel || ''
        const lines = (items || []).map((item) => `${item.marker}${item.seriesName}：${item.value ?? '—'}`)
        return [`<strong>${date}</strong>`, ...lines].join('<br/>')
      }
    },
    legend: { top: 0, left: props.legendAlign === 'left' ? 0 : 'center', right: props.legendAlign === 'left' ? 82 : undefined, selectedMode: true, itemWidth: 14, itemHeight: 8, textStyle: { color: '#64748B', fontSize: 11 }, data: [props.referenceName, props.forecastName, ...(props.extraSeriesName ? [props.extraSeriesName] : [])] },
    grid: { left: '2%', right: '4%', top: 40, bottom: 14, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: isBar,
      data: props.dates,
      axisLine: { lineStyle: { color: '#E2E8F0' } },
      axisLabel: { color: '#94A3B8', fontSize: 10, rotate: 0, interval: axisLabelInterval, formatter: formatAxisDate, hideOverlap: true }
    },
    yAxis: { type: 'value', name: props.yAxisName, nameGap: 13, nameTextStyle: { color: '#94A3B8', fontSize: 11, padding: [0, 0, 0, 2] }, splitLine: { lineStyle: { color: '#F1F5F9', type: 'dashed' } }, axisLabel: { color: '#94A3B8', fontSize: 11 } },
    dataZoom: [{ type: 'inside', start: 0, end: 100, zoomOnMouseWheel: true, moveOnMouseMove: true, moveOnMouseWheel: true }],
    series: [
      { name: props.referenceName, type: props.chartType, data: props.referenceValues, barMaxWidth: 18, barGap: '18%', smooth: 0.25, symbol: 'none', connectNulls: false, itemStyle: { color: props.referenceColor }, lineStyle: { width: 1.6, color: props.referenceColor } },
      {
        name: props.forecastName,
        type: props.chartType,
        data: props.forecastValues,
        barMaxWidth: 18,
        smooth: 0.25,
        symbol: 'none',
        connectNulls: false,
        itemStyle: { color: props.forecastColor },
        lineStyle: { width: 2.4, color: props.forecastColor },
        areaStyle: isBar ? undefined : { color: `${props.forecastColor}14` },
        markLine: props.forecastStartDate ? { z: 20, silent: true, symbol: 'none', lineStyle: { color: '#60a5fa', type: 'dashed', width: 1.3 }, label: { show: true, formatter: '预测起点', position: 'insideEndTop', color: '#2563eb', fontSize: 11, fontWeight: 600, backgroundColor: '#eff6ff', borderRadius: 3, padding: [3, 5] }, data: [{ xAxis: props.forecastStartDate }] } : undefined,
        markArea: props.forecastStartDate ? { z: 1, silent: true, itemStyle: { color: props.forecastAreaColor }, label: { show: true, formatter: '预测区间', position: 'insideTop', color: '#2563eb', fontSize: 11, fontWeight: 600, backgroundColor: 'rgba(239,246,255,.86)', borderRadius: 3, padding: [3, 5] }, data: [[{ xAxis: props.forecastStartDate }, { xAxis: props.dates[props.dates.length - 1] }]] } : undefined
      },
      ...(props.extraSeriesName ? [{ name: props.extraSeriesName, type: props.chartType, data: props.extraSeriesValues, barMaxWidth: 18, smooth: 0.25, symbol: 'none', connectNulls: false, itemStyle: { color: props.extraSeriesColor }, lineStyle: { width: 1.5, type: 'dashed', color: props.extraSeriesColor } }] : [])
    ]
  }, true)
  chart.resize()
})

let resizeObserver: ResizeObserver | undefined
onMounted(() => { render(); if (root.value) { resizeObserver = new ResizeObserver(() => chart?.resize()); resizeObserver.observe(root.value) } })
onBeforeUnmount(() => { resizeObserver?.disconnect(); chart?.dispose(); chart = null })
watch(() => [props.dates, props.referenceValues, props.forecastValues, props.extraSeriesValues, props.extraSeriesName, props.forecastStartDate, props.chartType, props.legendAlign, props.yAxisName, props.forecastAreaColor], render, { deep: true })
defineExpose({ getDataURL: (options?: object) => chart?.getDataURL(options), downloadChart })
</script>

<style scoped>
.forecast-comparison-chart-wrap{position:relative;width:100%;height:100%}
.forecast-comparison-chart{width:100%;height:100%}
.chart-download-button,.chart-reset-button{position:absolute;z-index:2;top:8px;display:grid;place-items:center;width:28px;height:28px;padding:0;color:#94a3b8;background:rgba(255,255,255,.82);border:1px solid #edf1f5;border-radius:7px;cursor:pointer;opacity:.68;transition:color .18s ease,border-color .18s ease,background-color .18s ease,opacity .18s ease}
.chart-download-button{right:8px}.chart-reset-button{right:42px}
.chart-download-button:hover,.chart-download-button:focus-visible,.chart-reset-button:hover,.chart-reset-button:focus-visible{color:#2563eb;background:#fff;border-color:#93c5fd;opacity:1;outline:none}
.chart-download-button svg,.chart-reset-button svg{width:16px;height:16px;fill:none;stroke:currentColor;stroke-linecap:round;stroke-linejoin:round;stroke-width:1.8}
</style>
