<template>
  <section class="training-card">
    <div v-if="!simple" class="title"><strong>训练信息与回测效果</strong><span>{{ statusText }}</span></div>
    <div v-if="!simple" class="basic-grid">
      <div><small>客户</small><b>{{ result.customerName || '-' }}</b></div>
      <div><small>区域 / 行业</small><b>{{ [result.regionName, result.industryName].filter(Boolean).join(' / ') || '-' }}</b></div>
      <div><small>最优模型</small><b>{{ result.bestModel || '-' }}</b></div>
      <div><small>模型版本</small><b>{{ result.modelVersion || '-' }}</b></div>
      <div><small>训练区间</small><b>{{ result.trainStartDate || '-' }} 至 {{ result.trainEndDate || '-' }}</b></div>
      <div><small>训练耗时</small><b>{{ durationText }}</b></div>
    </div>
    <div v-if="!simple" class="metric-grid">
      <div><small>MAPE</small><b>{{ percent(result.mape) }}</b></div>
      <div><small>WMAPE</small><b>{{ percent(result.wmape) }}</b></div>
      <div><small>SMAPE</small><b>{{ percent(result.smape) }}</b></div>
      <div><small>MAE</small><b>{{ number(result.mae) }}</b></div>
      <div><small>RMSE</small><b>{{ number(result.rmse) }}</b></div>
      <div><small>R²</small><b>{{ number(result.r2) }}</b></div>
    </div>
    <template v-if="result.backtestPoints?.length">
      <div class="chart-title"><strong>{{ simple ? '回测结果' : '回测：实际值与预测值' }}</strong><span v-if="!simple">{{ result.backtestPoints.length }} 个数据点</span><div v-else class="view-switch"><button type="button" :class="{ active: backtestView === 'chart' }" @click="backtestView = 'chart'">图表</button><button type="button" :class="{ active: backtestView === 'data' }" @click="backtestView = 'data'">数据对比</button></div></div>
      <ForecastComparisonChart
        v-if="!simple || backtestView === 'chart'"
        class="chart"
        :dates="result.backtestPoints.map((point) => point.date)"
        :reference-values="result.backtestPoints.map((point) => point.actualValue ?? null)"
        :forecast-values="result.backtestPoints.map((point) => point.predictedValue ?? null)"
        chart-type="line"
        reference-name="实际值"
        forecast-name="预测值"
        reference-color="#10b981"
        forecast-color="#3b82f6"
      />
      <div v-else class="backtest-data"><div class="backtest-head"><span>日期</span><span>实际值</span><span>回测预测值</span><span>绝对误差</span><span>误差率</span></div><div v-for="point in result.backtestPoints" :key="point.date" class="backtest-row"><span>{{ point.date }}</span><span>{{ number(point.actualValue) }}</span><span>{{ number(point.predictedValue) }}</span><span>{{ absoluteError(point) }}</span><span>{{ errorRate(point) }}</span></div></div>
    </template>
    <div v-else class="empty">该训练批次暂无回测明细，已展示现有训练信息和评估指标。</div>
    <div v-if="!simple" class="batch">训练批次：{{ result.batchNo }}</div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ForecastComparisonChart from '@/components/ForecastComparisonChart.vue'

interface BacktestPoint { date: string; actualValue?: number; predictedValue?: number; absoluteError?: number; errorRate?: number }
interface TrainingResult {
  batchNo: string; status?: string; agentName?: string; customerName?: string; regionName?: string; industryName?: string
  trainStartDate?: string; trainEndDate?: string; bestModel?: string; modelVersion?: string; trainDurationSeconds?: number
  mape?: number; wmape?: number; smape?: number; mae?: number; rmse?: number; r2?: number
  createdAt?: string; updatedAt?: string; backtestPoints?: BacktestPoint[]
}
const props = withDefaults(defineProps<{ result: TrainingResult; simple?: boolean }>(), { simple: false })
const backtestView = ref<'chart' | 'data'>('chart')
const number = (value?: number) => value == null || !Number.isFinite(Number(value)) ? '-' : Number(value).toFixed(2)
const percent = (value?: number) => value == null || !Number.isFinite(Number(value)) ? '-' : `${Number(value).toFixed(2)}%`
const statusText = computed(() => ({ SUCCESS: '训练完成', RUNNING: '训练中', FAILED: '训练失败' }[String(props.result.status || '').toUpperCase()] || props.result.status || '状态未知'))
const durationText = computed(() => props.result.trainDurationSeconds == null ? '-' : `${Number(props.result.trainDurationSeconds).toFixed(1)} 秒`)
const absoluteError = (point: BacktestPoint) => point.absoluteError == null ? (point.actualValue == null || point.predictedValue == null ? '-' : number(Math.abs(Number(point.actualValue) - Number(point.predictedValue)))) : number(point.absoluteError)
const errorRate = (point: BacktestPoint) => {
  if (point.errorRate != null) return `${Number(point.errorRate).toFixed(2)}%`
  if (point.actualValue == null || point.predictedValue == null || Number(point.actualValue) === 0) return '-'
  return `${(Math.abs(Number(point.actualValue) - Number(point.predictedValue)) / Math.abs(Number(point.actualValue)) * 100).toFixed(2)}%`
}
</script>

<style scoped>
.training-card{margin-top:10px;padding:16px;border:1px solid #e1e7f0;border-radius:12px;background:#fff;min-width:0}.title,.chart-title{display:flex;justify-content:space-between;align-items:center;gap:12px}.title span{padding:3px 8px;border-radius:12px;background:#ecfdf5;color:#047857;font-size:11px}.basic-grid,.metric-grid{display:grid;gap:9px;margin-top:13px}.basic-grid{grid-template-columns:repeat(3,1fr)}.metric-grid{grid-template-columns:repeat(6,1fr)}.basic-grid>div,.metric-grid>div{padding:9px;background:#f7f9fc;border-radius:8px;min-width:0}.basic-grid small,.basic-grid b,.metric-grid small,.metric-grid b{display:block}.basic-grid small,.metric-grid small,.chart-title span,.batch{color:#94a3b8;font-size:11px}.basic-grid b,.metric-grid b{margin-top:4px;color:#334155;font-size:12px;overflow-wrap:anywhere}.chart-title{margin-top:18px;padding-top:14px;border-top:1px solid #edf1f6}.chart{width:100%;height:300px;margin-top:8px}.empty{margin-top:15px;padding:12px;border-radius:8px;background:#f8fafc;color:#64748b;font-size:12px}.batch{margin-top:10px}.view-switch{display:flex;gap:4px;padding:3px;border-radius:7px;background:#f1f5f9}.view-switch button{height:27px;padding:0 9px;border:0;border-radius:5px;background:transparent;color:#64748b;cursor:pointer}.view-switch button.active{background:#fff;color:#315ecc;box-shadow:0 1px 3px rgba(15,23,42,.12)}.backtest-data{max-height:350px;overflow:auto;margin-top:10px;border:1px solid #e5eaf1;border-radius:8px}.backtest-head,.backtest-row{display:grid;grid-template-columns:1.2fr repeat(4,1fr);gap:10px;padding:9px 12px;font-size:12px}.backtest-head{position:sticky;top:0;background:#f8fafc;color:#64748b;font-weight:600}.backtest-row{border-top:1px solid #edf1f6;color:#334155}.backtest-head span:not(:first-child),.backtest-row span:not(:first-child){text-align:right}@media(max-width:760px){.basic-grid{grid-template-columns:1fr 1fr}.metric-grid{grid-template-columns:repeat(3,1fr)}.backtest-data{overflow-x:auto}.backtest-head,.backtest-row{min-width:620px}}
</style>
