<template><section><h4>{{ data.title }}</h4><div ref="container" class="content-chart"></div></section></template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
const props = defineProps<{ data: Record<string, any> }>()
const container = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
let observer: ResizeObserver | undefined
function render() {
  if (!chart) return
  const rows = props.data.rows || []
  chart.setOption({ tooltip: { trigger: 'axis' }, legend: {}, grid: { containLabel: true },
    xAxis: { type: 'category', data: rows.map((row: any) => row[props.data.xField]) },
    yAxis: { type: 'value' }, series: (props.data.yFields || []).map((field: string) => ({
      name: field, type: String(props.data.chartType).toUpperCase() === 'BAR' ? 'bar' : 'line',
      data: rows.map((row: any) => row[field] ?? null)
    })) }, true)
}
onMounted(() => { if (container.value) { chart = echarts.init(container.value); render(); observer = new ResizeObserver(() => chart?.resize()); observer.observe(container.value) } })
watch(() => props.data, render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>
<style scoped>.content-chart { height: 320px; width: 100%; min-width: 240px; }</style>
