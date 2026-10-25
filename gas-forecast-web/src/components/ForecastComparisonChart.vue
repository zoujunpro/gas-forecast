<template>
  <div ref="root" class="forecast-comparison-chart" role="img" :aria-label="`${referenceName}与${forecastName}趋势图`" />
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
}>(), {
  referenceValues: () => [],
  chartType: 'bar',
  referenceName: '去年同期',
  forecastName: '最新预测',
  referenceColor: '#9ca3af',
  forecastColor: '#b91c1c'
})

const root = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const render = () => nextTick(() => {
  if (!root.value) return
  chart = echarts.getInstanceByDom(root.value) || echarts.init(root.value)
  const isBar = props.chartType === 'bar'
  chart.setOption({
    animationDuration: 350,
    tooltip: { trigger: 'axis', axisPointer: { type: isBar ? 'shadow' : 'line' }, backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#E2E8F0', textStyle: { color: '#0F172A', fontSize: 12 } },
    legend: { top: 0, textStyle: { color: '#64748B', fontSize: 11 }, data: [props.referenceName, props.forecastName] },
    grid: { left: '2%', right: '4%', top: 40, bottom: 36, containLabel: true },
    xAxis: { type: 'category', boundaryGap: isBar, data: props.dates, axisLine: { lineStyle: { color: '#E2E8F0' } }, axisLabel: { color: '#94A3B8', fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8', fontSize: 11 } },
    dataZoom: [{ type: 'inside', start: 0, end: 100, zoomOnMouseWheel: true }, { type: 'slider', start: 0, end: 100, height: 10, bottom: 28, borderColor: '#E2E8F0', fillerColor: `${props.forecastColor}1a`, handleStyle: { color: props.forecastColor } }],
    series: [
      { name: props.referenceName, type: props.chartType, data: props.referenceValues, barMaxWidth: 18, barGap: '18%', smooth: 0.25, symbol: 'none', connectNulls: false, itemStyle: { color: props.referenceColor }, lineStyle: { width: 1.5, color: props.referenceColor } },
      { name: props.forecastName, type: props.chartType, data: props.forecastValues, barMaxWidth: 18, smooth: 0.25, symbol: 'none', connectNulls: false, itemStyle: { color: props.forecastColor }, lineStyle: { width: 1.5, color: props.forecastColor }, areaStyle: isBar ? undefined : { color: `${props.forecastColor}14` } }
    ]
  }, true)
  chart.resize()
})

let resizeObserver: ResizeObserver | undefined
onMounted(() => { render(); if (root.value) { resizeObserver = new ResizeObserver(() => chart?.resize()); resizeObserver.observe(root.value) } })
onBeforeUnmount(() => { resizeObserver?.disconnect(); chart?.dispose(); chart = null })
watch(() => [props.dates, props.referenceValues, props.forecastValues, props.chartType], render, { deep: true })
defineExpose({ getDataURL: (options?: object) => chart?.getDataURL(options) })
</script>

<style scoped>
.forecast-comparison-chart{width:100%;height:100%}
</style>
