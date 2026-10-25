<template>
  <section class="record-page">
    <PageBreadcrumb />
    <AppTablePanel>
      <template #filters>
        <el-input v-model="filters.forecastNo" clearable placeholder="预测编号" @keyup.enter="loadData" />
        <el-select v-model="filters.agentCode" clearable placeholder="来源智能体"><el-option v-for="item in agents" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-input v-model="filters.regionCode" clearable placeholder="地区名称或编码" @keyup.enter="loadData" />
        <el-input v-model="filters.industryCode" clearable placeholder="行业名称或编码" @keyup.enter="loadData" />
        <el-input v-model="filters.customerCode" clearable placeholder="客户名称或编码" @keyup.enter="loadData" />
        <el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="item in statuses" :key="item" :label="item" :value="item" /></el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
      </template>
      <AppTable v-loading="loading" :data="rows" border stripe>
        <el-table-column label="预测编号" min-width="230"><template #default="{ row }"><span class="no-wrap">{{ row.forecastNo || '-' }}</span></template></el-table-column>
        <el-table-column label="来源智能体" min-width="130"><template #default="{ row }">{{ agentName(row.agentCode) }}</template></el-table-column>
        <el-table-column label="地区" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ row.regionName || row.regionCode || '-' }}</template></el-table-column>
        <el-table-column label="行业" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ row.industryName || row.industryCode || '-' }}</template></el-table-column>
        <el-table-column label="客户" min-width="170" show-overflow-tooltip><template #default="{ row }">{{ row.customerName || row.customerCode || '-' }}</template></el-table-column>
        <el-table-column label="历史开始日期" width="125"><template #default="{ row }"><span class="no-wrap">{{ row.historyStartDate || '-' }}</span></template></el-table-column>
        <el-table-column label="历史结束日期" width="125"><template #default="{ row }"><span class="no-wrap">{{ row.historyEndDate || '-' }}</span></template></el-table-column>
        <el-table-column label="训练样本数" width="110" align="right"><template #default="{ row }"><span class="no-wrap">{{ row.historyRows == null ? '-' : `${row.historyRows} 条` }}</span></template></el-table-column>
        <el-table-column label="预测开始日期" width="125"><template #default="{ row }"><span class="no-wrap">{{ row.forecastStartDate || '-' }}</span></template></el-table-column>
        <el-table-column label="预测周期" width="110"><template #default="{ row }"><span class="no-wrap">{{ row.forecastHorizon == null ? '-' : `${row.forecastHorizon} ${row.forecastUnit || ''}` }}</span></template></el-table-column>
        <el-table-column prop="status" label="状态" width="105"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="bestModel" label="最佳模型" min-width="150" show-overflow-tooltip />
        <el-table-column label="MAPE" width="100"><template #default="{ row }">{{ percent(row.mape) }}</template></el-table-column>
        <el-table-column label="开始时间" width="175"><template #default="{ row }"><span class="no-wrap">{{ dateTime(row.startedAt) }}</span></template></el-table-column>
        <el-table-column label="完成时间" width="175"><template #default="{ row }"><span class="no-wrap">{{ dateTime(row.finishedAt) }}</span></template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right" class-name="action-column" label-class-name="action-column"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template></el-table-column>
      </AppTable>
      <template #footer><AppPagination v-model:current-page="page" v-model:page-size="size" :total="total" @current-change="loadData" @size-change="loadData" /></template>
    </AppTablePanel>
    <el-dialog v-model="detailVisible" width="calc(100vw - 64px)" top="24px" class="record-result-dialog" destroy-on-close>
      <template #header>
        <div class="record-result-heading"><h2>执行记录详情</h2><p>查看本次预测的训练、回测、预测结果及 AI 研判</p></div>
      </template>
      <main v-if="detail" class="record-result">
        <section class="record-hero">
          <div><p class="record-kicker">本次预测</p><h3>{{ [detail.regionName || detail.regionCode, detail.industryName || detail.industryCode, detail.customerName || detail.customerCode].filter(Boolean).join(' / ') || '预测执行记录' }}</h3><p class="record-meta">预测编号：{{ detail.forecastNo || '-' }}　·　发起：{{ dateTime(detail.startedAt) }}　·　完成：{{ dateTime(detail.finishedAt) }}</p></div>
          <el-tag :type="statusType(detail.status)" size="large">{{ statusText(detail.status) }}</el-tag>
        </section>
        <section class="record-summary-grid">
          <article><small>预测开始时间</small><strong>{{ detail.forecastStartDate || '-' }}</strong></article>
          <article><small>预测周期</small><strong>{{ detail.forecastHorizon ?? '-' }} {{ detail.forecastUnit || '' }}</strong></article>
          <article><small>最佳模型</small><strong>{{ detail.bestModel || '-' }}</strong></article>
          <article><small>回测 MAPE</small><strong class="primary-metric">{{ percent(detail.mape) }}</strong></article>
        </section>
        <el-alert v-if="detail.status === 'FAILED'" title="本次预测执行失败" :description="detail.errorMessage || '未返回具体失败原因'" type="error" :closable="false" show-icon />
        <template v-else>
          <section class="record-metrics"><span>模型评估指标</span><div><article v-for="metric in secondaryMetricCards" :key="metric.label"><small>{{ metric.label }}</small><strong>{{ metric.value }}</strong></article></div></section>
          <el-tabs v-model="activeResultTab" class="record-tabs">
            <el-tab-pane label="训练数据" name="dataset">
              <section class="tab-panel">
                <div class="sample-title"><h5>训练样本明细</h5><span>{{ detail.historyRows ?? trainingDataRows.length }} 条模型训练入参</span></div>
                <el-table v-if="trainingDataRows.length" class="training-data-table" :data="trainingDataRows" border stripe max-height="420"><el-table-column v-for="column in trainingDataColumns" :key="column" :prop="column" :label="columnLabel(column)" min-width="140" show-overflow-tooltip><template #default="{ row }"><span :class="{ 'empty-value': row[column] == null || row[column] === '' }">{{ formatTrainingValue(row[column]) }}</span></template></el-table-column></el-table><el-empty v-else description="本次执行未保存训练样本明细" />
              </section>
            </el-tab-pane>
            <el-tab-pane label="模型比对" name="candidates">
              <section class="tab-panel"><div class="tab-heading"><h4>候选模型评估与选择</h4><span>已成功 {{ detail.successfulCandidateCount ?? 0 }} / {{ detail.candidateCount ?? 0 }} 个候选模型</span></div>
                <el-table v-if="candidateRows.length" :data="candidateRows" border stripe max-height="480"><el-table-column prop="rank" label="排名" width="80" /><el-table-column prop="modelName" label="模型名称" min-width="180" show-overflow-tooltip /><el-table-column prop="selected" label="最终选择" width="110" /><el-table-column prop="mape" label="MAPE" width="110" /><el-table-column prop="wmape" label="WMAPE" width="110" /><el-table-column prop="rmse" label="RMSE" width="110" /><el-table-column prop="mae" label="MAE" width="110" /><el-table-column prop="r2" label="R²" width="90" /></el-table><el-empty v-else description="本次执行未返回候选模型比对结果" />
              </section>
            </el-tab-pane>
            <el-tab-pane label="回测结果" name="backtest"><section class="tab-panel"><AiTrainingResult :result="trainingPresentation" simple /></section></el-tab-pane>
            <el-tab-pane label="预测结果" name="forecast"><section class="tab-panel"><AiForecastResult v-if="forecastPresentation.points.length" :result="forecastPresentation" :show-backtest="false" simple readonly /><el-empty v-else description="暂无预测结果" /><section class="record-analysis"><div><h4>AI 解析</h4><small>基于本次训练、回测与预测结果生成</small></div><p>{{ detail.analysisContent || '本次执行暂未生成 AI 解析。' }}</p></section></section></el-tab-pane>
          </el-tabs>
        </template>
      </main>
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { getJson, listPage } from '@/api/management'
import AiTrainingResult from '@/views/ai/AiTrainingResult.vue'
import AiForecastResult from '@/views/ai/AiForecastResult.vue'
defineOptions({ name: 'ModelExecuteRecordManagement' })
const page = ref(1), size = ref(20), total = ref(0), loading = ref(false), rows = ref<any[]>([]), detail = ref<any>(), detailVisible = ref(false), activeResultTab = ref('dataset')
const filters = reactive({ forecastNo: '', agentCode: '', regionCode: '', industryCode: '', customerCode: '', status: '' }); const statuses = ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED']
const agents = [{ value: 'general', label: '通用智能助手' }, { value: 'short-term', label: '短期客户预测' }, { value: 'monthly-sales', label: '月度销量预测' }, { value: 'winter-supply', label: '冬季保供预测' }]
const agentName = (code?: string) => ({ general: '通用智能助手', 'short-term': '短期客户预测', 'monthly-sales': '月度销量预测', 'winter-supply': '冬季保供预测' }[String(code || '').toLowerCase()] || code || '-')
const loadData = async () => { loading.value = true; try { const data:any = await listPage('/model-execute-record', { page: page.value, size: size.value, ...filters }); rows.value = data.records || []; total.value = data.total || 0 } finally { loading.value = false } }
const openDetail = async (row:any) => { const response:any = await getJson(`/model-execute-record/${row.forecastNo}`); detail.value = response.data || response; activeResultTab.value = 'dataset'; detailVisible.value = true }
const percent = (value?:number) => value == null ? '-' : `${(value * 100).toFixed(2)}%`
const dateRange = (start?: string, end?: string, rows?: number) => {
  const period = start || end ? `${start || '-'} 至 ${end || '-'}` : '-'
  return rows == null ? period : `${period}（${rows} 条）`
}
const forecastPeriod = (record: any) => {
  if (!record.forecastStartDate && record.forecastHorizon == null) return '-'
  return `${record.forecastStartDate || '-'} 起，${record.forecastHorizon ?? '-'} ${record.forecastUnit || ''}`
}
const duration = (seconds?: number) => seconds == null ? '-' : `${Number(seconds).toFixed(1)} 秒`
/** 后端以 UTC 返回瞬时时间，页面统一按中国时区显示，避免暴露 T、毫秒与 +00:00。 */
const dateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').replace(/\.\d+(?:Z|[+-]\d\d:\d\d)?$/, '')
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).formatToParts(date)
  const part = (type: string) => parts.find(item => item.type === type)?.value || ''
  return `${part('year')}-${part('month')}-${part('day')} ${part('hour')}:${part('minute')}:${part('second')}`
}
const statusText = (value?: string) => ({ PENDING: '等待执行', RUNNING: '执行中', SUCCESS: '执行完成', FAILED: '执行失败' }[String(value || '').toUpperCase()] || value || '-')
const parseJson = (value?: string) => { try { return value ? JSON.parse(value) : {} } catch { return {} } }
const pointDate = (point: any) => point?.date || point?.stat_date || point?.statDate || point?.forecast_date || point?.forecastDate
const actualValue = (point: any) => point?.actual_value ?? point?.actualValue ?? point?.actual
const predictedValue = (point: any) => point?.predicted_value ?? point?.predictedValue ?? point?.predicted
const forecastValue = (point: any) => point?.forecast_value ?? point?.forecastValue ?? point?.prediction ?? point?.value
const backtestPoints = computed(() => {
  const source = parseJson(detail.value?.backtestResultJson)
  const points = Array.isArray(source.points) ? source.points : (Array.isArray(source.rolling_backtest_results) ? source.rolling_backtest_results : [])
  return points.map((point: any) => ({ date: pointDate(point), actualValue: actualValue(point), predictedValue: predictedValue(point) })).filter((point: any) => point.date)
})
const trainingPresentation = computed(() => {
  const source = parseJson(detail.value?.trainingResultJson); const metrics = source.metrics || {}
  return { batchNo: detail.value?.forecastNo || '-', status: detail.value?.status, customerName: detail.value?.customerName, regionName: detail.value?.regionName, industryName: detail.value?.industryName,
    trainStartDate: detail.value?.historyStartDate, trainEndDate: detail.value?.historyEndDate, bestModel: detail.value?.bestModel, modelVersion: detail.value?.modelVersion,
    trainDurationSeconds: source.train_duration_seconds ?? source.trainDurationSeconds ?? detail.value?.executeDurationSeconds,
    mape: metrics.mape ?? detail.value?.mape, wmape: metrics.wmape ?? detail.value?.wmape, smape: metrics.smape ?? detail.value?.smape, mae: metrics.mae ?? detail.value?.mae, rmse: metrics.rmse ?? detail.value?.rmse, r2: metrics.r2 ?? detail.value?.r2, backtestPoints: backtestPoints.value }
})
const trainingDataRows = computed<Record<string, any>[]>(() => {
  const request = parseJson(detail.value?.requestParam)
  const rows = [request.history_dataset, request.historyDataSet, request.dataset, request.history_data, request.historyData, request.training_data, request.trainingData].find(Array.isArray)
  return Array.isArray(rows) ? rows.filter((row: unknown) => row && typeof row === 'object') : []
})
const trainingDataColumns = computed(() => Array.from(new Set(trainingDataRows.value.flatMap(row => Object.keys(row)))))
const columnLabel = (column: string) => ({ date: '日期', stat_date: '统计日期', statDate: '统计日期', gas_sales: '天然气销量', gasSales: '天然气销量', sales: '销量', sales_value: '销量', salesValue: '销量', value: '数值', customer_name: '客户', customerName: '客户', max_temp: '最高气温', min_temp: '最低气温', avg_temp: '平均气温', feels_like_max: '最高体感温度', feels_like_min: '最低体感温度', feels_like_avg: '平均体感温度', dew_point: '露点温度', humidity: '湿度', precipitation: '降水量', wind_gust: '阵风风速', wind_speed: '风速', sea_level_pressure: '海平面气压', cloud_cover: '云量', visibility: '能见度', solar_radiation: '太阳辐射', solar_energy: '太阳能量', uv_index: '紫外线指数', hdd: '采暖度日' }[column] || column)
const formatTrainingValue = (value: unknown) => value == null || value === '' ? '—' : String(value)
const candidateRows = computed(() => {
  const training = parseJson(detail.value?.trainingResultJson)
  const candidates = Array.isArray(training.candidate_evaluations) ? training.candidate_evaluations : []
  return candidates.map((candidate: any, index: number) => { const metrics = candidate.metrics || {}; return { rank: candidate.rank ?? index + 1, modelName: candidate.model_name || candidate.modelName || candidate.model_type || '-', selected: candidate.selected ? '是' : '否', mape: metrics.mape ?? '-', wmape: metrics.wmape ?? '-', rmse: metrics.rmse ?? '-', mae: metrics.mae ?? '-', r2: metrics.r2 ?? '-' } }).sort((left: any, right: any) => Number(right.selected === '是') - Number(left.selected === '是') || Number(left.rank) - Number(right.rank))
})
const forecastPresentation = computed(() => {
  const source = parseJson(detail.value?.forecastResultJson); const rawPoints = Array.isArray(source.points) ? source.points : []
  const points = rawPoints.map((point: any) => ({ date: pointDate(point), value: forecastValue(point) })).filter((point: any) => point.date && point.value != null)
  const lastYearPoints = Array.isArray(detail.value?.lastYearPoints) ? detail.value.lastYearPoints.map((point: any) => ({ date: pointDate(point), value: forecastValue(point) })).filter((point: any) => point.date) : []
  const values = points.map((point: any) => Number(point.value)).filter(Number.isFinite)
  return { batchNo: detail.value?.forecastNo || '-', customerName: detail.value?.customerName, points, lastYearPoints, backtestPoints: backtestPoints.value, training: trainingPresentation.value,
    average: values.length ? values.reduce((sum: number, value: number) => sum + value, 0) / values.length : 0, minimum: values.length ? Math.min(...values) : 0, maximum: values.length ? Math.max(...values) : 0 }
})
const secondaryMetricCards = computed(() => [
  { label: 'WMAPE', value: percent(detail.value?.wmape) }, { label: 'SMAPE', value: percent(detail.value?.smape) },
  { label: 'RMSE', value: detail.value?.rmse ?? '-' }, { label: 'MAE', value: detail.value?.mae ?? '-' }, { label: 'R²', value: detail.value?.r2 ?? '-' }, { label: '执行耗时', value: duration(detail.value?.executeDurationSeconds) }
])
const statusType = (value:string) => value === 'SUCCESS' ? 'success' : value === 'FAILED' ? 'danger' : value === 'RUNNING' ? 'warning' : 'info'
onMounted(loadData)
</script>
<style scoped>
.record-result-heading h2{margin:0;color:#16233a;font-size:20px;letter-spacing:.2px}.record-result-heading p{margin:5px 0 0;color:#7a8799;font-size:13px}.record-result{padding:0 4px 16px}.record-hero{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:18px 2px 20px;border-bottom:1px solid #e9edf3}.record-kicker{margin:0 0 5px;color:#3671c9;font-size:12px;font-weight:600;letter-spacing:.6px}.record-hero h3{margin:0;color:#16233a;font-size:21px}.record-meta{margin:8px 0 0;color:#7a8799;font-size:12px}.record-summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin:18px 0 0;border:1px solid #e4e9f0;border-radius:8px;overflow:hidden}.record-summary-grid article{position:relative;padding:14px 16px;background:#fff}.record-summary-grid article+article{border-left:1px solid #e9edf3}.record-summary-grid small,.record-summary-grid strong,.record-metrics small,.record-metrics strong{display:block}.record-summary-grid small,.record-metrics small{color:#7a8799;font-size:12px}.record-summary-grid strong{margin-top:6px;color:#24324a;font-size:15px;overflow-wrap:anywhere}.record-summary-grid .primary-metric{color:#2765b2;font-size:18px}.record-metrics{display:flex;align-items:center;gap:20px;padding:13px 2px;border-bottom:1px solid #e9edf3}.record-metrics>span{flex:0 0 auto;color:#526174;font-size:12px;font-weight:600}.record-metrics>div{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));flex:1}.record-metrics article{padding:0 14px;border-left:1px solid #edf0f4}.record-metrics strong{margin-top:4px;color:#26354b;font-size:14px}.record-tabs{margin-top:10px}.tab-panel{padding:16px 0 4px}.training-data-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.training-data-head h4,.sample-title h5,.feature-grid h5{margin:0;color:#24324a;font-size:15px}.training-data-head p,.feature-grid p{margin:5px 0 0;color:#7a8799;font-size:13px}.training-data-head>span{padding:5px 10px;border-radius:99px;background:#edf5ff;color:#356db9;font-size:12px;white-space:nowrap}.training-scope-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin-top:15px;border:1px solid #e5eaf1;border-radius:8px;overflow:hidden}.training-scope-grid article{padding:13px 14px;background:#fafbfd}.training-scope-grid article+article{border-left:1px solid #e5eaf1}.training-scope-grid small,.training-scope-grid strong{display:block}.training-scope-grid small{color:#7a8799;font-size:12px}.training-scope-grid strong{margin-top:6px;color:#28364c;font-size:13px;overflow-wrap:anywhere}.feature-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:14px}.feature-grid article{padding:15px 16px;border:1px solid #e5eaf1;border-radius:8px;background:#fff}.feature-tags{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.feature-tags .el-tag{height:auto;padding:5px 9px;background:#f8faff;border-color:#d8e5f5;color:#3d5f8e}.feature-tags b,.feature-tags small{display:block;line-height:1.3}.feature-tags small{margin-top:2px;color:#7b8798;font-size:11px}.sample-title{display:flex;justify-content:space-between;align-items:baseline;gap:12px;margin:22px 0 10px}.sample-title span{color:#7a8799;font-size:12px}.record-analysis{margin-top:18px;padding:18px 20px;border-left:3px solid #3976c9;border-radius:4px;background:#f7faff}.record-analysis>div{display:flex;align-items:baseline;gap:12px}.record-analysis p{margin:9px 0 0;color:#435168;font-size:14px;line-height:1.8;white-space:pre-wrap}@media(max-width:900px){.record-summary-grid{grid-template-columns:1fr 1fr}.record-summary-grid article:nth-child(3){border-left:0;border-top:1px solid #e9edf3}.record-summary-grid article:nth-child(4){border-top:1px solid #e9edf3}.record-metrics{align-items:flex-start;flex-direction:column;gap:10px}.record-metrics>div{grid-template-columns:repeat(3,minmax(0,1fr));width:100%}.training-scope-grid{grid-template-columns:1fr 1fr}.training-scope-grid article:nth-child(3){border-left:0;border-top:1px solid #e5eaf1}.training-scope-grid article:nth-child(4){border-top:1px solid #e5eaf1}}@media(max-width:600px){.record-hero,.training-data-head{align-items:flex-start;flex-direction:column}.record-summary-grid,.training-scope-grid,.feature-grid{grid-template-columns:1fr}.record-summary-grid article+article,.training-scope-grid article+article{border-left:0;border-top:1px solid #e9edf3}.record-metrics>div{grid-template-columns:1fr 1fr}.record-metrics article:nth-child(odd){border-left:0}.sample-title{align-items:flex-start;flex-direction:column}}
.no-wrap{white-space:nowrap}.record-result-heading h2{margin:0;color:#16233a;font-size:20px;letter-spacing:.2px}.record-result-heading p{margin:5px 0 0;color:#7a8799;font-size:13px}.record-result{padding:0 4px 16px}.record-hero{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:18px 2px 20px;border-bottom:1px solid #e9edf3}.record-kicker{margin:0 0 5px;color:#3671c9;font-size:12px;font-weight:600;letter-spacing:.6px}.record-hero h3{margin:0;color:#16233a;font-size:21px}.record-meta{margin:8px 0 0;color:#7a8799;font-size:12px}.record-summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin:18px 0 0;border:1px solid #e4e9f0;border-radius:8px;overflow:hidden}.record-summary-grid article{position:relative;padding:14px 16px;background:#fff}.record-summary-grid article+article{border-left:1px solid #e9edf3}.record-summary-grid small,.record-summary-grid strong,.record-metrics small,.record-metrics strong{display:block}.record-summary-grid small,.record-metrics small{color:#7a8799;font-size:12px}.record-summary-grid strong{margin-top:6px;color:#24324a;font-size:15px;overflow-wrap:anywhere}.record-summary-grid .primary-metric{color:#2765b2;font-size:18px}.record-metrics{display:flex;align-items:center;gap:20px;padding:13px 2px;border-bottom:1px solid #e9edf3}.record-metrics>span{flex:0 0 auto;color:#526174;font-size:12px;font-weight:600}.record-metrics>div{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));flex:1}.record-metrics article{padding:0 14px;border-left:1px solid #edf0f4}.record-metrics strong{margin-top:4px;color:#26354b;font-size:14px}.record-tabs{margin-top:10px}.tab-panel{padding:16px 0 4px}.training-data-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.training-data-head h4,.sample-title h5,.feature-grid h5{margin:0;color:#24324a;font-size:15px}.training-data-head p,.feature-grid p{margin:5px 0 0;color:#7a8799;font-size:13px}.training-data-head>span{padding:5px 10px;border-radius:99px;background:#edf5ff;color:#356db9;font-size:12px;white-space:nowrap}.training-scope-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin-top:15px;border:1px solid #e5eaf1;border-radius:8px;overflow:hidden}.training-scope-grid article{padding:13px 14px;background:#fafbfd}.training-scope-grid article+article{border-left:1px solid #e5eaf1}.training-scope-grid small,.training-scope-grid strong{display:block}.training-scope-grid small{color:#7a8799;font-size:12px}.training-scope-grid strong{margin-top:6px;color:#28364c;font-size:13px;overflow-wrap:anywhere}.feature-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:14px}.feature-grid article{padding:15px 16px;border:1px solid #e5eaf1;border-radius:8px;background:#fff}.feature-tags{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.feature-tags .el-tag{height:auto;padding:5px 9px;background:#f8faff;border-color:#d8e5f5;color:#3d5f8e}.feature-tags b,.feature-tags small{display:block;line-height:1.3}.feature-tags small{margin-top:2px;color:#7b8798;font-size:11px}.sample-title{display:flex;justify-content:space-between;align-items:baseline;gap:12px;margin:22px 0 10px}.sample-title span{color:#7a8799;font-size:12px}.record-analysis{margin-top:18px;padding:18px 20px;border-left:3px solid #3976c9;border-radius:4px;background:#f7faff}.record-analysis>div{display:flex;align-items:baseline;gap:12px}.record-analysis p{margin:9px 0 0;color:#435168;font-size:14px;line-height:1.8;white-space:pre-wrap}@media(max-width:900px){.record-summary-grid{grid-template-columns:1fr 1fr}.record-summary-grid article:nth-child(3){border-left:0;border-top:1px solid #e9edf3}.record-summary-grid article:nth-child(4){border-top:1px solid #e9edf3}.record-metrics{align-items:flex-start;flex-direction:column;gap:10px}.record-metrics>div{grid-template-columns:repeat(3,minmax(0,1fr));width:100%}.training-scope-grid{grid-template-columns:1fr 1fr}.training-scope-grid article:nth-child(3){border-left:0;border-top:1px solid #e5eaf1}.training-scope-grid article:nth-child(4){border-top:1px solid #e5eaf1}}@media(max-width:600px){.record-hero,.training-data-head{align-items:flex-start;flex-direction:column}.record-summary-grid,.training-scope-grid,.feature-grid{grid-template-columns:1fr}.record-summary-grid article+article,.training-scope-grid article+article{border-left:0;border-top:1px solid #e9edf3}.record-metrics>div{grid-template-columns:1fr 1fr}.record-metrics article:nth-child(odd){border-left:0}.sample-title{align-items:flex-start;flex-direction:column}}
.training-data-table{border-color:#d5deea!important}.training-data-table :deep(th.el-table__cell){background:#eaf1fa!important;color:#263b58!important;font-weight:700}.training-data-table :deep(td.el-table__cell){color:#344964!important}.training-data-table :deep(.el-table__row--striped td.el-table__cell){background:#f5f8fc!important}.training-data-table :deep(.el-table__cell){border-color:#dfe7f1!important}.empty-value{color:#8c9bae;font-weight:600}
.record-result-heading p,.record-meta,.sample-title span{color:#64748b!important}.record-summary-grid small,.record-metrics small{color:#64748b!important;font-weight:600}.record-summary-grid strong,.record-metrics strong{color:#1e293b!important;font-weight:700}.record-metrics>span{color:#334155!important;font-weight:700}.record-tabs :deep(.el-tabs__item){color:#475569!important;font-weight:600}.record-tabs :deep(.el-tabs__item.is-active){color:#0284c7!important;font-weight:700}
</style>
