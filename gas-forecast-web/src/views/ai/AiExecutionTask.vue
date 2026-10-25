<template>
  <div class="task-presentation">
  <section v-loading="loading && !hasLoaded" class="task-card">
    <template v-if="isBatchTask">
      <div class="task-head"><div><strong>批量预测任务</strong><small>{{ overview.taskNo }} · 共 {{ batchTotal }} 个{{ executionTargetLabel }}</small></div><el-tag :type="statusType(overview.status)">{{ statusText(overview.status) }}</el-tag></div>
      <div class="task-metrics batch-metrics">
        <div><small>{{ executionTargetLabel }}总数</small><b>{{ batchTotal }}</b></div><div><small>已完成</small><b class="success-value">{{ overview.completed || 0 }}</b></div>
        <div><small>执行中</small><b class="running-value">{{ overview.running || 0 }}</b></div><div><small>待执行</small><b>{{ overview.pending || 0 }}</b></div>
        <div><small>失败</small><b :class="{ danger: Number(overview.failed || 0) > 0 }">{{ overview.failed || 0 }}</b></div>
      </div>
      <el-progress class="task-progress" :percentage="batchProgressPercent" :stroke-width="6" :show-text="false" :status="overview.status === 'FAILED' ? 'exception' : overview.status === 'SUCCESS' || overview.status === 'COMPLETED' ? 'success' : undefined" />
      <div class="list-title"><strong>{{ executionTargetLabel }}执行明细</strong><small>展示 {{ records.length }} 条，共 {{ batchTotal }} 个{{ executionTargetLabel }}</small></div>
      <div class="batch-detail-list">
        <div v-for="item in records" :key="item.detailNo" :class="['batch-detail-row', batchDetailClass(item)]">
          <div><strong>{{ item.targetName || '-' }}</strong><small>{{ [item.regionName, item.industryName].filter(Boolean).join(' · ') || '—' }}</small></div>
          <span>{{ detailProgressText(item) }} · {{ detailCompletedSteps(item) }} / {{ (item.steps || []).length || 6 }} 步</span>
          <el-tag size="small" :class="['batch-status', batchDetailClass(item)]" :type="statusType(item.status)">{{ statusText(item.status) }}</el-tag>
        </div>
      </div>
    </template>
    <template v-else-if="singleTask">
      <div class="task-head"><div><strong>任务执行情况</strong><small>{{ singleTask.targetName }} · {{ overview.taskNo }}</small></div><el-tag :type="statusType(displayStatus)">{{ statusText(displayStatus) }}</el-tag></div>
      <div class="task-metrics">
        <div><small>预测周期</small><b>{{ forecastPeriods }} 天</b></div><div><small>执行进度</small><b>{{ completedSteps }} / 6</b></div>
        <div><small>最佳模型</small><b>{{ bestModelDisplay || '-' }}</b></div><div><small>回测 MAPE</small><b class="danger">{{ modelMape || '-' }}</b></div>
      </div>
      <el-progress class="task-progress" :percentage="progressPercent" :stroke-width="6" :show-text="false" :status="displayStatus === 'FAILED' ? 'exception' : displayStatus === 'SUCCESS' ? 'success' : undefined" />
      <div class="list-title"><strong>执行过程</strong><small>{{ currentText({ ...singleTask, status: displayStatus }) }}</small></div>
      <div class="inline-steps">
          <div v-for="step in presentedSteps" :key="step.stepNo || step.stepCode" class="step" :class="[String(step.status || '').toLowerCase(), { expanded: isExpanded(singleTask.detailNo, step) }]">
            <button type="button" class="step-head" @click="toggleStep(singleTask.detailNo, step)">
              <i></i><strong>{{ step.stepOrder }}. {{ step.stepName }}</strong>
              <span class="step-summary">{{ summary(step) }}</span>
              <b class="chevron">{{ isExpanded(singleTask.detailNo, step) ? '⌃' : '⌄' }}</b>
            </button>
            <p v-if="step.errorMessage" class="error">{{ step.errorMessage }}</p>
            <div v-if="isExpanded(singleTask.detailNo, step)" v-loading="detailLoading[singleTask.detailNo]" class="step-detail">
              <template v-if="detailStep(singleTask.detailNo, step)">
                <div v-for="entry in detailEntries(detailStep(singleTask.detailNo, step))" :key="entry.label" class="detail-entry"><span>{{ entry.label }}</span><b>{{ entry.value }}</b></div>
              </template>
              <span v-else class="empty-detail">暂无可展开的详情</span>
            </div>
          </div>
      </div>
    </template>
    <el-alert v-if="loadError" class="load-error" type="error" :closable="false" show-icon>
      <template #title>任务数据加载失败：{{ loadError }}</template>
    </el-alert>
    <el-empty v-if="!records.length && !loading" description="暂无任务明细" :image-size="48" />
  </section>
  <!-- 结果与过程是同一任务的连续输出，但始终作为相邻区块，不嵌入过程卡片。 -->
  <transition name="result-reveal">
    <AiForecastResult v-if="forecastResult && resultRevealStage" class="task-result" :result="forecastResult" :reveal-stage="resultRevealStage" readonly />
  </transition>
  <transition name="result-reveal">
    <section v-if="forecastResult && resultRevealStage >= 4 && typedAnalysis" class="task-analysis">
      <div class="analysis-title"><strong>AI 智能研判报告</strong><small>{{ analysisTyping ? '正在生成' : '已生成' }}</small></div>
      <p>{{ typedAnalysis }}</p>
    </section>
  </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getJson } from '@/api/management'
import AiForecastResult from './AiForecastResult.vue'
const props = defineProps<{ taskNo: string }>()
const emit = defineEmits<{ (event: 'forecast-completed', result: Record<string, any>): void; (event: 'content-rendered'): void }>()
const loading = ref(false), hasLoaded = ref(false), loadError = ref(''), overview = ref<Record<string, any>>({}), records = ref<Record<string, any>[]>([]), total = ref(0), pageNo = ref(1)
const expandedStepKeys = ref<Record<string, boolean>>({}), detailSteps = ref<Record<string, Record<string, any>[]>>({}), detailLoading = ref<Record<string, boolean>>({})
// 服务端单页最多返回 100 条；批量任务前端会继续读取后续页，列表内滚动展示全部客户。
const pageSize = 100
// 快任务也需要让用户看清过程；慢任务始终以真实完成时间为准。
const MIN_STEP_DISPLAY_MS = 5_000
const presentationNow = ref(Date.now())
const presentationStartAt = ref(Date.now())
let presentationTimer: ReturnType<typeof setTimeout> | undefined
let analysisTimer: ReturnType<typeof setTimeout> | undefined
let resultRevealTimer: ReturnType<typeof setTimeout> | undefined
const forecastResult = ref<any>(null)
const resultLoading = ref(false)
const resultRevealStage = ref(0)
const typedAnalysis = ref('')
const analysisTyping = ref(false)
const singleTask = computed(() => records.value[0])
const isBatchTask = computed(() => Number(overview.value.total || total.value || 0) > 1)
const batchTotal = computed(() => Number(overview.value.total || total.value || 0))
const batchProgressPercent = computed(() => Math.max(0, Math.min(100, Number(overview.value.progressPercent || 0))))
const executionTargetLabel = computed(() => {
  const dimension = String(overview.value.dimensionType || records.value[0]?.dimensionType || '').toUpperCase()
  return ({ CUSTOMER: '客户', REGION: '地区', INDUSTRY: '行业' }[dimension] || '对象')
})
const terminalStep = (status?: string) => ['SUCCESS', 'COMPLETED', 'FAILED', 'CANCELLED', 'SKIPPED'].includes(String(status || '').toUpperCase())
const statusText = (status?: string) => ({ PENDING: '待执行', WAITING: '待执行', RUNNING: '执行中', TRAINING: '训练中', FORECASTING: '预测中', SUCCESS: '已完成', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消', SKIPPED: '已跳过' }[String(status || '').toUpperCase()] || status || '待执行')
const statusType = (status?: string) => ({ SUCCESS: 'success', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info', RUNNING: 'primary', TRAINING: 'primary', FORECASTING: 'primary' }[String(status || '').toUpperCase()] || 'info') as any
const currentText = (item: Record<string, any>) => item.status === 'PENDING' || item.status === 'WAITING' ? '等待执行' : item.status === 'FAILED' ? '任务执行失败' : item.status === 'SUCCESS' ? '预测任务已完成' : '正在处理，请稍候'
const rawSteps = computed<Record<string, any>[]>(() => singleTask.value?.steps || [])
const succeeded = (status?: string) => ['SUCCESS', 'COMPLETED'].includes(String(status || '').toUpperCase())
const detailCompletedSteps = (item: Record<string, any>) => (item.steps || []).filter((step: Record<string, any>) => succeeded(step.status)).length
const detailProgressText = (item: Record<string, any>) => {
  const steps = item.steps || []
  const active = steps.find((step: Record<string, any>) => ['RUNNING', 'TRAINING', 'FORECASTING'].includes(String(step.status || '').toUpperCase()))
  if (active) return `执行：${active.stepName || '处理中'}`
  const failed = steps.find((step: Record<string, any>) => ['FAILED', 'CANCELLED'].includes(String(step.status || '').toUpperCase()))
  if (failed) return `停在：${failed.stepName || '任务失败'}`
  if (steps.length && steps.every((step: Record<string, any>) => succeeded(step.status))) return '已完成'
  const next = steps.find((step: Record<string, any>) => !succeeded(step.status))
  return next ? `等待：${next.stepName || '执行'}` : '等待执行'
}
const batchDetailClass = (item: Record<string, any>) => {
  const status = String(item.status || '').toUpperCase()
  if (succeeded(status)) return 'completed'
  if (['FAILED', 'CANCELLED'].includes(status)) return 'failed'
  if (['RUNNING', 'TRAINING', 'FORECASTING'].includes(status)) return 'running'
  return 'pending'
}
const stepTime = (value: any) => {
  const time = value ? new Date(value).getTime() : NaN
  return Number.isFinite(time) ? time : undefined
}
/**
 * 每一步的“可展示完成时刻” = max(真实完成时刻, 上一步展示完成 + 5 秒)。
 * 因而真实耗时超过 20 秒时不人为延迟，快任务才补足过程感。
 */
const presentationTimeline = computed(() => {
  let previous = stepTime(singleTask.value?.createdAt) || presentationStartAt.value
  return rawSteps.value.map((step, index) => {
    const rawStatus = String(step.status || 'PENDING').toUpperCase()
    const terminal = terminalStep(rawStatus)
    const actualFinishedAt = terminal ? stepTime(step.updatedAt) : undefined
    const revealAt = terminal ? Math.max(previous + MIN_STEP_DISPLAY_MS, actualFinishedAt || previous + MIN_STEP_DISPLAY_MS) : undefined
    if (revealAt) previous = revealAt
    return { index, rawStatus, revealAt }
  })
})
const presentedSteps = computed<Record<string, any>[]>(() => rawSteps.value.map((step, index) => {
  const point = presentationTimeline.value[index]
  const rawStatus = point?.rawStatus || String(step.status || 'PENDING').toUpperCase()
  if (terminalStep(rawStatus) && point?.revealAt && presentationNow.value >= point.revealAt) return { ...step, status: rawStatus }
  const previousAllVisible = presentationTimeline.value.slice(0, index).every(item => item.revealAt && presentationNow.value >= item.revealAt)
  // 已经真实完成但还在最短展示窗口内：只显示“处理中”，不透出模型、指标或预测结果。
  if (terminalStep(rawStatus)) return previousAllVisible
    ? { ...step, status: 'RUNNING', message: undefined, errorMessage: undefined, presentationWaiting: true }
    : { ...step, status: 'PENDING', message: undefined, errorMessage: undefined, presentationWaiting: true }
  if (previousAllVisible && rawStatus !== 'PENDING' && rawStatus !== 'WAITING') return { ...step, status: rawStatus }
  return { ...step, status: 'PENDING', message: undefined, errorMessage: undefined }
}))
const completedSteps = computed(() => presentedSteps.value.filter(step => succeeded(step.status)).length)
const progressPercent = computed(() => Math.round((completedSteps.value / 6) * 100))
const stepData = (code: string) => detailStep(singleTask.value?.detailNo, { stepCode: code })
  || (singleTask.value?.steps || []).find((step: Record<string, any>) => step.stepCode === code)
const presentedStep = (code: string) => presentedSteps.value.find(step => step.stepCode === code)
const stepVisibleAsCompleted = (code: string) => succeeded(presentedStep(code)?.status)
const stepMetrics = (code: string) => {
  const raw = parseJson(stepData(code)?.metricsJson) || parseJson(stepData(code)?.outputJson) || {}
  const nested = parseJson(raw.metrics) || raw.metrics || {}
  return { ...raw, ...(typeof nested === 'object' ? nested : {}) }
}
const metricText = (key: string, suffix = '') => {
  const value = stepMetrics('MODEL_SELECT')[key]
  return value == null || value === '' || !Number.isFinite(Number(value)) ? '' : `${Number(value).toFixed(2)}${suffix}`
}
const modelSummaryText = computed(() => String(stepData('MODEL_SELECT')?.message || ''))
const modelMape = computed(() => {
  if (!stepVisibleAsCompleted('MODEL_SELECT')) return ''
  const structured = metricText('mape', '%') || metricText('name', '%')
  if (structured) return structured
  const matched = modelSummaryText.value.match(/MAPE\s*[：:]?\s*([\d.]+)%/i)
  return matched ? `${Number(matched[1]).toFixed(2)}%` : ''
})
const bestModel = computed(() => {
  if (!stepVisibleAsCompleted('MODEL_SELECT')) return ''
  const structured = stepMetrics('MODEL_SELECT').best_model || stepMetrics('MODEL_SELECT').selected_model_name
    || stepMetrics('MODEL_SELECT').selected_model_code || stepMetrics('MODEL_SELECT').model_code
  if (structured) return structured
  return modelSummaryText.value.match(/最佳模型\s*[：:]?\s*([^；;，,\s]+)/)?.[1] || ''
})
/** 顶部概览使用可读名称；展开步骤详情仍保留模型平台原始编码，便于追溯。 */
const bestModelDisplay = computed(() => {
  const model = String(bestModel.value || '')
  const normalized = model.toLowerCase().replace(/[-_]/g, '')
  if (normalized.includes('prophet') && normalized.includes('lightgbm')) return 'Prophet + LightGBM'
  if (normalized.includes('lightgbm')) return 'LightGBM'
  if (normalized.includes('xgboost')) return 'XGBoost'
  if (normalized.includes('randomforest')) return '随机森林'
  return model.replace(/_/g, ' ')
})
const forecastPeriods = computed(() => stepMetrics('FORECAST').forecastPeriods || stepMetrics('FORECAST').periods || 45)
const displayStatus = computed(() => {
  const steps = presentedSteps.value
  if (!steps.length) return singleTask.value?.status || 'PENDING'
  if (steps.some((step: Record<string, any>) => ['FAILED', 'CANCELLED'].includes(String(step.status).toUpperCase()))) return 'FAILED'
  if (steps.length >= 6 && steps.every((step: Record<string, any>) => terminalStep(step.status))) return 'SUCCESS'
  return steps.some((step: Record<string, any>) => String(step.status).toUpperCase() === 'RUNNING') ? 'RUNNING' : singleTask.value?.status || 'PENDING'
})
const defaultSummary = (step: Record<string, any>) => ({ SALES_DATA_PREPARE: '等待准备销量数据', FACTOR_DATA_PREPARE: '等待准备基础特征', DYNAMIC_INDICATOR: '等待识别动态特征', MODEL_SELECT: '等待训练并评估模型', FORECAST: '等待生成预测结果', FORECAST_ANALYSIS: '等待生成预测解读' }[step.stepCode] || '')
const summary = (step: Record<string, any>) => {
  if (step.presentationWaiting) return runningSummary(step)
  if (String(step.status).toUpperCase() === 'PENDING') return defaultSummary(step)
  if (String(step.status).toUpperCase() === 'FAILED') return `${step.stepName}失败，请查看下方原因`
  const message = String(step.message || '')
  if (!message) return defaultSummary(step)
  if (step.stepCode === 'SALES_DATA_PREPARE' && /基础特征=|训练数据=/.test(message)) {
    const count = message.match(/训练数据=(\d+)/)?.[1]
    return count ? `有效销量数据 ${count} 条` : '销量数据准备完成'
  }
  if (step.stepCode === 'FACTOR_DATA_PREPARE' && /模型平台校验|dataset=|训练请求/.test(message)) return '基础特征准备完成'
  if (step.stepCode === 'DYNAMIC_INDICATOR' && (message.startsWith('{') || message.includes('"status"'))) return '动态特征识别完成，点击查看详情'
  if (step.stepCode === 'MODEL_SELECT' && /请求模型接口|训练指标和回测结果已入库|模型平台训练完成/.test(message)) return '模型训练与评估完成，点击查看详情'
  return message
}
const runningSummary = (step: Record<string, any>) => ({ SALES_DATA_PREPARE: '正在核验历史销量数据', FACTOR_DATA_PREPARE: '正在准备基础特征', DYNAMIC_INDICATOR: '正在识别动态特征', MODEL_SELECT: '正在训练、评估并筛选模型', FORECAST: '正在生成未来销量预测', FORECAST_ANALYSIS: '正在生成预测结果解读' }[step.stepCode] || '正在处理')
const stepKey = (detailNo: string, step: Record<string, any>) => `${detailNo}:${step.stepCode}`
const isExpanded = (detailNo: string, step: Record<string, any>) => Boolean(expandedStepKeys.value[stepKey(detailNo, step)])
const loadStepDetails = async (detailNo: string) => {
  if (detailSteps.value[detailNo] || detailLoading.value[detailNo]) return
  detailLoading.value = { ...detailLoading.value, [detailNo]: true }
  try {
    const result = await getJson(`/execution-tasks/details/${encodeURIComponent(detailNo)}/steps`)
    detailSteps.value = { ...detailSteps.value, [detailNo]: result.data?.steps || [] }
  } finally { detailLoading.value = { ...detailLoading.value, [detailNo]: false } }
}
const toggleStep = async (detailNo: string, step: Record<string, any>) => {
  const key = stepKey(detailNo, step)
  expandedStepKeys.value = { ...expandedStepKeys.value, [key]: !expandedStepKeys.value[key] }
  if (expandedStepKeys.value[key]) await loadStepDetails(detailNo)
}
const detailStep = (detailNo: string, step: Record<string, any>) => detailSteps.value[detailNo]?.find(item => item.stepCode === step.stepCode)
const parseJson = (value: any) => { if (!value || typeof value !== 'string') return value; try { return JSON.parse(value) } catch { return value } }
const labelOf = (key: string) => ({ salesCount: '有效销量数据', startDate: '开始日期', endDate: '结束日期', missingSalesCount: '缺失销量数据', featureCount: '基础特征数', weatherFeatureCount: '天气特征数', features: '特征列表', candidate_count: '候选指标数', available_count: '可用指标数', selected_indexes: '已选指标', selected_model_name: '最佳模型', best_model: '最佳模型', mape: 'MAPE', wmape: 'WMAPE', rmse: 'RMSE', mae: 'MAE', r2: 'R²', analysis: '智能解读', model: '解读模型', forecastBatchNo: '预测批次', backtestDays: '回测天数', forecastDays: '预测天数' }[key] || key)
const readable = (value: any) => {
  if (Array.isArray(value)) {
    // 数组内含对象时 join 会渲染为 [object Object]；步骤详情只显示数量，
    // 完整点位由紧随其后的预测结果图表和明细表承担。
    return value.some(item => item && typeof item === 'object') ? `共 ${value.length} 条` : value.join('、')
  }
  return typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value ?? '')
}
const displayValue = (key: string, value: any) => {
  const text = readable(value)
  // 历史解读已按旧提示词保存为编号/Markdown；展示时兼容清理，不修改原始审计数据。
  return key === 'analysis' ? text.replace(/\*\*/g, '').replace(/[①②③]\s*/g, '').replace(/\s*\n\s*/g, ' ') : text
}
const modelDetailEntries = (raw: Record<string, any>) => {
  const metrics = parseJson(raw.metrics) || raw.metrics || {}
  const metricValue = (key: string) => metrics[key] ?? raw[key]
  // 部分模型平台把主指标放在 metrics.name 中，兼容该历史返回格式。
  const mape = metricValue('mape') ?? metricValue('name')
  return [
    ['最佳模型', raw.selected_model_name || raw.selected_model_code || raw.best_model || raw.model_code],
    ['候选模型数', raw.requested_candidate_count ?? raw.candidate_count],
    ['训练成功数', raw.successful_candidate_count ?? raw.success_count],
    ['参与排名数', raw.ranked_candidate_count ?? raw.ranked_count],
    ['MAPE', mape == null ? undefined : `${Number(mape).toFixed(2)}%`],
    ['MAE', metricValue('mae')],
    ['RMSE', metricValue('rmse')],
    ['R²', metricValue('r2')],
    ['训练批次', raw.train_batch_no || raw.trainBatchNo]
  ].filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([label, value]) => ({ label, value: readable(value) }))
}
const forecastDetailEntries = (raw: Record<string, any>) => {
  const metadata = parseJson(raw.metadata) || raw.metadata || {}
  const points = Array.isArray(raw.points) ? raw.points : []
  return [
    ['预测点数', points.length ? `${points.length} 天` : undefined],
    ['预测批次', metadata.forecast_batch_no || metadata.forecastNo || metadata.batch_no],
    ['预测模型', metadata.selected_model_name || metadata.best_model || metadata.model_name]
  ].filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([label, value]) => ({ label, value: readable(value) }))
}
const detailEntries = (step: Record<string, any>) => {
  const value = parseJson(step.metricsJson) || parseJson(step.outputJson) || parseJson(step.inputJson)
  if (typeof value === 'string') return [{ label: '执行详情', value }]
  if (!value || typeof value !== 'object') return []
  if (step.stepCode === 'MODEL_SELECT') return modelDetailEntries(value)
  if (step.stepCode === 'FORECAST') return forecastDetailEntries(value)
  // 步骤卡展示统计结果，不在聊天流内堆放完整特征编码列表。
  // 特征明细由数据中心/模型详情承载；这里仅保留基础、天气等数量。
  return Object.entries(value)
    .filter(([key, item]) => key !== 'features' && item !== null && item !== undefined && item !== '')
    .map(([key, item]) => ({ label: labelOf(key), value: displayValue(key, item) }))
}
const analysisText = computed(() => {
  const step = detailStep(singleTask.value?.detailNo, { stepCode: 'FORECAST_ANALYSIS' })
  const raw = parseJson(step?.metricsJson) || parseJson(step?.outputJson) || {}
  const saved = typeof raw === 'object' && raw ? String(raw.analysis || raw.content || raw.result || '') : ''
  if (saved) return saved
  // 兼容已完成的历史任务：当时第六步只保存“结果已整理”，没有 analysis 字段。
  // 新任务由后端落库研判；旧任务根据已落库的同一份回测/预测结果补出简短客观说明。
  const result = forecastResult.value
  if (!result?.training?.bestModel) return ''
  const mape = Number(result.training.mape)
  const accuracy = !Number.isFinite(mape) ? '回测指标未返回，暂不对精度作判断'
    : mape <= 10 ? '回测精度较好，可作为近期经营参考'
    : mape <= 20 ? '回测精度可用，建议结合实际业务变化持续跟踪'
    : '回测误差偏高，建议将本次结果作为趋势参考，并结合实际销量持续校正'
  const points = Array.isArray(result.points) ? result.points : []
  const average = points.length ? points.reduce((sum: number, item: any) => sum + Number(item.value || 0), 0) / points.length : undefined
  return `本次预测采用${result.training.bestModel}${Number.isFinite(mape) ? `，MAPE 为${mape.toFixed(2)}%` : ''}。${accuracy}。${average === undefined ? '' : `未来${points.length}天预测均值为${average.toFixed(2)}。`}`
})
const presentationFinished = computed(() => !isBatchTask.value && rawSteps.value.length === 6
  && rawSteps.value.every(step => succeeded(step.status))
  && presentedSteps.value.every(step => succeeded(step.status)))
const normalizeResult = (data: Record<string, any>) => {
  const record = data.record || {}
  // 任务结果接口保留了模型平台三段原始 JSON 快照。这里是对话结果卡唯一的
  // 适配层：先解析快照，再转换为卡片所需的统一点位和指标，不能把 JSON 字符串
  // 当作已经展开的对象使用。
  const asObject = (value: any): Record<string, any> => {
    if (!value) return {}
    if (typeof value === 'object') return value
    try { return JSON.parse(value) } catch { return {} }
  }
  const training = asObject(data.training || record.trainingResultJson)
  const backtest = asObject(data.backtest || record.backtestResultJson)
  const forecast = asObject(data.forecast || record.forecastResultJson)
  const trainingMetrics = asObject(training.metrics)
  const point = (item: any) => ({
    date: item.date || item.forecastDate || item.forecast_date || item.trainDate || item.statDate || item.stat_date,
    value: Number(item.value ?? item.forecastValue ?? item.forecast_value ?? item.predictedValue ?? item.predicted_value ?? item.prediction ?? 0)
  })
  const backtestPoint = (item: any) => ({
    date: item.date || item.trainDate || item.statDate || item.stat_date,
    actualValue: Number(item.actualValue ?? item.actual_value ?? item.actual ?? 0),
    predictedValue: Number(item.predictedValue ?? item.predicted_value ?? item.predicted ?? item.prediction ?? 0)
  })
  return {
    ...record,
    // 不展开 data，避免 data.training/backtest/forecast 的原始字符串覆盖下方结构化结果。
    batchNo: data.batchNo || record.forecastNo || record.batchNo || record.forecastBatchNo || singleTask.value?.forecastNo,
    customerName: data.customerName || data.customer?.customerName || singleTask.value?.targetName,
    regionName: data.regionName || data.customer?.regionName || record.regionName,
    industryName: data.industryName || data.customer?.industryName || record.industryName,
    training: {
      ...trainingMetrics,
      ...training,
      batchNo: record.forecastNo,
      bestModel: record.bestModel || training.selected_model_name || training.best_model || training.model_name,
      mape: record.mape ?? trainingMetrics.mape ?? training.mape
    },
    points: (Array.isArray(data.points) ? data.points : forecast.points || []).map(point),
    lastYearPoints: (data.lastYearPoints || []).map(point),
    backtestPoints: (Array.isArray(data.backtestPoints) ? data.backtestPoints : backtest.points || backtest.rolling_backtest_results || []).map(backtestPoint)
  }
}
const clearAnalysisTyping = () => { if (analysisTimer) clearTimeout(analysisTimer); analysisTimer = undefined; analysisTyping.value = false }
const clearResultReveal = () => { if (resultRevealTimer) clearTimeout(resultRevealTimer); resultRevealTimer = undefined; resultRevealStage.value = 0 }
const revealAnalysis = () => {
  clearAnalysisTyping()
  const text = analysisText.value.trim()
  if (!text) return
  // 已经过完整展示时间轴的历史任务直接恢复全文；当前会话中的新完成任务才逐字呈现。
  const lastReveal = presentationTimeline.value[presentationTimeline.value.length - 1]?.revealAt || 0
  if (presentationNow.value - lastReveal > 1_000) { typedAnalysis.value = text; return }
  typedAnalysis.value = ''
  analysisTyping.value = true
  let cursor = 0
  const typeNext = () => {
    cursor = Math.min(text.length, cursor + (text[cursor] === '。' ? 1 : 2))
    typedAnalysis.value = text.slice(0, cursor)
    if (cursor >= text.length) { analysisTyping.value = false; return }
    analysisTimer = setTimeout(typeNext, 32)
  }
  typeNext()
}
const loadPresentationResult = async () => {
  const detailNo = singleTask.value?.detailNo
  if (!detailNo || resultLoading.value || forecastResult.value) return
  resultLoading.value = true
  try {
    const [resultResponse] = await Promise.all([
      getJson(`/execution-tasks/details/${encodeURIComponent(detailNo)}/result`),
      loadStepDetails(detailNo)
    ])
    forecastResult.value = normalizeResult(resultResponse.data || {})
    const lastReveal = presentationTimeline.value[presentationTimeline.value.length - 1]?.revealAt || 0
    // 历史任务恢复时直接呈现；当前任务按内容块依次输出，避免结果突然堆满页面。
    if (presentationNow.value - lastReveal > 1_000) {
      resultRevealStage.value = 4
      emit('forecast-completed', forecastResult.value)
      await nextTick()
      revealAnalysis()
      return
    }
    const revealNext = (stage: number) => {
      resultRevealStage.value = stage
      if (stage < 4) {
        resultRevealTimer = setTimeout(() => revealNext(stage + 1), 900)
        return
      }
      emit('forecast-completed', forecastResult.value)
      void nextTick().then(revealAnalysis)
    }
    revealNext(1)
  } finally { resultLoading.value = false }
}
const schedulePresentationTick = () => {
  if (presentationTimer) clearTimeout(presentationTimer)
  presentationTimer = undefined
  presentationNow.value = Date.now()
  const next = presentationTimeline.value.map(item => item.revealAt).filter((time): time is number => Boolean(time && time > presentationNow.value)).sort((a, b) => a - b)[0]
  if (next) presentationTimer = setTimeout(schedulePresentationTick, Math.max(1, next - presentationNow.value + 8))
  if (presentationFinished.value) void loadPresentationResult()
}
/**
 * 首次读取步骤快照，运行中仅接收 SSE 事件。切换会话时旧请求可能晚到，
 * 必须以 taskNo 和序号校验响应，不能让旧任务的数据覆盖当前卡片。
 */
let refreshSequence = 0
let eventSource: EventSource | undefined
const closeEventStream = () => { eventSource?.close(); eventSource = undefined }
const applyStepEvent = (payload: Record<string, any>) => {
  const detailNo = payload.detailNo
  const incoming = payload.step
  if (!detailNo || !incoming) return
  records.value = records.value.map(item => item.detailNo !== detailNo ? item : {
    ...item,
    steps: (item.steps || []).map((step: Record<string, any>) => step.stepCode === incoming.stepCode ? { ...step, ...incoming } : step)
  })
  if (detailSteps.value[detailNo]) {
    detailSteps.value = {
      ...detailSteps.value,
      [detailNo]: detailSteps.value[detailNo].map(step => step.stepCode === incoming.stepCode ? { ...step, ...incoming } : step)
    }
  }
  schedulePresentationTick()
  if (isBatchTask.value) {
    void fetchOverview(props.taskNo).then(result => { overview.value = result.data || overview.value }).catch(() => undefined)
  }
  if (incoming.stepCode === 'FORECAST_ANALYSIS' && terminalStep(incoming.status)) closeEventStream()
}
const openEventStream = () => {
  if (eventSource || !props.taskNo || !singleTask.value || terminalStep(singleTask.value.status)) return
  eventSource = new EventSource(`/execution-tasks/${encodeURIComponent(props.taskNo)}/events`)
  eventSource.addEventListener('task_step_updated', event => {
    try { applyStepEvent(JSON.parse((event as MessageEvent).data)) } catch { /* 忽略格式异常事件，快照仍可恢复当前状态。 */ }
  })
}
const fetchOverview = (taskNo: string) => getJson(`/execution-tasks/${encodeURIComponent(taskNo)}`)
const fetchDetails = (taskNo: string, currentPage: number) => getJson(`/execution-tasks/${encodeURIComponent(taskNo)}/details?pageNo=${currentPage}&pageSize=${pageSize}`)
const fetchAllDetails = async (taskNo: string) => {
  const all: Record<string, any>[] = []
  let currentPage = 1
  let expectedTotal = 0
  while (true) {
    const result = await fetchDetails(taskNo, currentPage)
    const data = result.data || {}
    const pageRecords = data.records || []
    all.push(...pageRecords)
    expectedTotal = Number(data.total || expectedTotal)
    if (!pageRecords.length || all.length >= expectedTotal || pageRecords.length < pageSize) return { records: all, total: expectedTotal }
    currentPage++
  }
}
const refresh = async () => {
  const taskNo = props.taskNo
  const sequence = ++refreshSequence
  // 首次加载需要遮罩；后续静默刷新，避免每 5 秒遮住内容、触发页面明显重排。
  if (!hasLoaded.value) loading.value = true
  loadError.value = ''
  try {
    const overviewResult = await fetchOverview(taskNo)
    const detailsData = await fetchAllDetails(taskNo)
    // 请求已经过期：例如从任务 A 切换到任务 B 后，任务 A 的轮询才返回。
    if (sequence !== refreshSequence || taskNo !== props.taskNo) return
    overview.value = overviewResult.data || {}
    records.value = detailsData.records
    total.value = detailsData.total
    presentationStartAt.value = stepTime(records.value[0]?.createdAt) || Date.now()
    presentationNow.value = Date.now()
    hasLoaded.value = true
    // 任务明细异步加载会增高消息卡；通知父聊天容器重新定位到最新内容。
    await nextTick()
    emit('content-rendered')
    openEventStream()
    schedulePresentationTick()
  } catch (error) {
    if (sequence !== refreshSequence || taskNo !== props.taskNo) return
    loadError.value = error instanceof Error ? error.message : '未知错误'
  } finally {
    if (sequence === refreshSequence && taskNo === props.taskNo) loading.value = false
  }
}
// 对话中的任务卡只在进入会话、切换任务时加载一次。
// 不在组件内定时刷新，避免用户阅读或滚动时页面被异步数据重排。
onMounted(() => { void refresh() })
onBeforeUnmount(() => { closeEventStream(); if (presentationTimer) clearTimeout(presentationTimer); clearAnalysisTyping(); clearResultReveal() })
watch(() => props.taskNo, () => {
  // 先让旧请求失效，再加载新任务；清空旧画面，避免短暂展示成另一个任务。
  refreshSequence++
  pageNo.value = 1
  overview.value = {}
  records.value = []
  total.value = 0
  hasLoaded.value = false
  expandedStepKeys.value = {}
  detailSteps.value = {}
  forecastResult.value = null
  clearResultReveal()
  typedAnalysis.value = ''
  clearAnalysisTyping()
  if (presentationTimer) clearTimeout(presentationTimer)
  closeEventStream()
  void refresh()
})
</script>

<style scoped>
.task-presentation{width:100%;max-width:100%;overflow-anchor:none}.task-card{box-sizing:border-box;width:100%;max-width:100%;margin-top:10px;padding:18px;border:1px solid #dfe7f2;border-radius:12px;background:#fff;font-size:14px}.task-head,.list-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.task-head strong{display:block;color:#1e293b;font-size:16px}.task-head small,.list-title small{display:block;margin-top:5px;color:#94a3b8;font-size:13px}.task-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:16px 0 9px;padding:12px;border-radius:8px;background:#f7f9fc}.task-metrics>div{min-width:0}.task-metrics small,.task-metrics b{display:block}.task-metrics small{color:#94a3b8;font-size:13px}.task-metrics b{margin-top:5px;color:#334155;font-size:18px;line-height:1.25;overflow-wrap:anywhere;word-break:break-word}.task-progress{display:block;margin:0 2px}.danger,.error{color:#dc2626!important}.list-title{margin-top:16px;padding-top:14px;border-top:1px solid #edf1f6;font-size:14px}.inline-steps{display:grid;grid-template-columns:minmax(0,1fr);margin-top:9px}.step{min-width:0;border-bottom:1px solid #edf1f6}.step:last-child{border-bottom:0}.step-head{display:flex;align-items:center;width:100%;min-width:0;gap:9px;min-height:56px;padding:10px 4px;border:0;background:transparent;text-align:left;cursor:pointer}.step.running{margin:2px -8px;padding:0 8px;border-bottom-color:transparent;border-left:3px solid #2563eb;border-radius:7px;background:#eff6ff}.step i{width:10px;height:10px;flex:0 0 10px;border-radius:50%;background:#94a3b8}.step.success i{background:#22c55e}.step.running i,.step.training i,.step.forecasting i{background:#2563eb;box-shadow:0 0 0 0 rgba(37,99,235,.45);animation:step-pulse 1.4s infinite}.step.failed i{background:#ef4444}.step.running .step-head strong{color:#1d4ed8}.step.running .step-summary{color:#2563eb;font-weight:500}.step-head strong{flex:0 0 auto;color:#334155;font-size:14px}.step-summary{overflow:hidden;min-width:0;flex:1;color:#64748b;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.chevron{color:#94a3b8;font-size:18px}.step p{margin:0 4px 9px;font-size:14px}.step-detail{box-sizing:border-box;width:100%;min-width:0;margin:0 0 10px;padding:11px 12px;border-radius:6px;background:#f8fafc}.detail-entry{display:grid;grid-template-columns:100px minmax(0,1fr);gap:12px;padding:5px 0;font-size:14px;line-height:22px}.detail-entry span{color:#94a3b8}.detail-entry b{min-width:0;color:#334155;font-weight:500;overflow-wrap:anywhere;word-break:break-word;white-space:normal}.task-model{display:flex;align-items:baseline;gap:9px;margin-top:14px;padding-top:13px;border-top:1px solid #edf1f6}.task-model small{color:#94a3b8;font-size:13px}.task-model b{color:#334155;font-size:14px}.empty-detail{font-size:14px;color:#94a3b8}.load-error{margin-top:12px}.task-result,.task-analysis{margin-top:14px}.task-analysis{padding:18px 20px;border:1px solid #dfe7f2;border-radius:12px;background:#fff}.analysis-title{display:flex;align-items:center;justify-content:space-between;gap:12px;color:#1e293b;font-size:16px}.analysis-title small{padding:3px 8px;border-radius:99px;background:#ecfdf3;color:#15803d;font-size:12px}.task-analysis p{margin:12px 0 0;color:#475569;font-size:14px;line-height:1.8;white-space:pre-wrap}.result-reveal-enter-active{transition:opacity .36s ease,transform .36s ease}.result-reveal-enter-from{opacity:0;transform:translateY(8px)}@keyframes step-pulse{70%{box-shadow:0 0 0 7px rgba(37,99,235,0)}}@media(max-width:680px){.task-metrics{grid-template-columns:repeat(2,minmax(0,1fr));gap:6px;padding:9px}.task-metrics b{font-size:16px}.step-summary{display:none}}
.task-card{container-type:inline-size}
@container (max-width:560px){.task-metrics{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.task-metrics b{font-size:16px;overflow-wrap:normal;word-break:normal}.step-summary{display:none}}
.batch-metrics{grid-template-columns:repeat(5,minmax(0,1fr))}.success-value{color:#16a34a!important}.running-value{color:#2563eb!important}.batch-detail-list{display:grid;max-height:420px;margin-top:9px;overflow-y:auto;overscroll-behavior:contain}.batch-detail-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:12px;min-height:58px;padding:9px 4px;border-bottom:1px solid #edf1f6}.batch-detail-row:last-child{border-bottom:0}.batch-detail-row strong,.batch-detail-row small{display:block}.batch-detail-row strong{color:#334155;font-size:14px}.batch-detail-row small{margin-top:4px;color:#94a3b8;font-size:13px}.batch-detail-row>span{color:#64748b;font-size:13px;white-space:nowrap}@container (max-width:560px){.batch-metrics{grid-template-columns:repeat(5,minmax(0,1fr));gap:5px;padding:9px 7px}.batch-metrics small{font-size:11px}.batch-metrics b{font-size:15px}.batch-detail-row{grid-template-columns:minmax(0,1fr) auto auto;gap:7px}.batch-detail-row>span{font-size:11px}}
.batch-status.completed{border-color:#bbf7d0;background:#f0fdf4;color:#15803d}.batch-status.failed{border-color:#fecaca;background:#fef2f2;color:#dc2626}.batch-status.running,.batch-status.pending{border-color:#e2e8f0;background:#f8fafc;color:#64748b}.batch-status.running{display:inline-flex;align-items:center;gap:4px;animation:batch-status-breathe 1.5s ease-in-out infinite}.batch-status.running::before{width:6px;height:6px;border-radius:50%;background:#94a3b8;content:"";animation:batch-status-dot .9s ease-in-out infinite}@keyframes batch-status-breathe{50%{border-color:#94a3b8;background:#f1f5f9}}@keyframes batch-status-dot{50%{background:#475569;transform:scale(1.55)}}
</style>
