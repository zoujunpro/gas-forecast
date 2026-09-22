<template>
  <section class="forecast-page">
    <PageBreadcrumb />
    <AppTablePanel>
      <template #filters>
        <el-input v-model="keyword" clearable placeholder="搜索预测名称" :prefix-icon="Search" @keyup.enter="loadData" />
        <el-select v-model="agentCode" clearable placeholder="全部智能体">
          <el-option v-for="item in agents" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      </template>
      <template #actions><el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button></template>
      <AppTable v-loading="loading" :data="rows" border stripe @row-click="openInstances">
        <el-table-column prop="forecastName" label="预测名称" min-width="190" fixed />
        <el-table-column prop="agentCode" label="智能体" min-width="140"><template #default="{ row }">{{ agentLabel(row.agentCode) }}</template></el-table-column>
        <el-table-column prop="regionName" label="区域" min-width="110" />
        <el-table-column prop="industryName" label="行业" min-width="110" />
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column prop="forecastStartDate" label="预测开始日期" width="125" />
        <el-table-column prop="forecastHorizon" label="预测步长" width="100" align="right" />
        <el-table-column prop="forecastFrequency" label="时间颗粒度" width="110" />
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="createdByName" label="创建人" width="110" />
        <el-table-column label="创建日期" width="120"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="330" fixed="right"><template #default="{ row }"><span @click.stop><el-button link type="success" :loading="runningCode === row.id" @click="openPredict(row)">发起预测</el-button><el-button link type="primary" @click="openInstances(row)">查看预测实例</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></span></template></el-table-column>
      </AppTable>
      <template #footer><AppPagination v-model:current-page="page" v-model:page-size="size" :total="total" @current-change="loadData" @size-change="loadData" /></template>
    </AppTablePanel>

    <el-dialog v-model="drawerVisible" :title="form.id ? '编辑预测配置' : '新增预测配置'" width="760px" class="forecast-config-dialog" destroy-on-close align-center>
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <div class="form-grid">
          <el-form-item label="预测名称" prop="forecastName"><el-input v-model="form.forecastName" maxlength="128" show-word-limit placeholder="请输入预测名称" /></el-form-item>
          <el-form-item label="模型训练配置" prop="trainConfigCode"><el-select v-model="form.trainConfigCode" filterable placeholder="请选择模型训练配置" @change="handleTrainConfigChange"><el-option v-for="item in trainConfigOptions" :key="item.trainCode" :label="`${item.trainName}（${item.trainCode}）`" :value="item.trainCode" /></el-select></el-form-item>
          <el-form-item label="预测开始日期" prop="forecastStartDate"><el-date-picker v-model="form.forecastStartDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择预测开始日期" /></el-form-item>
          <el-form-item label="预测步长" prop="forecastHorizon"><el-input-number v-model="form.forecastHorizon" :min="1" :max="366" controls-position="right" /></el-form-item>
          <el-form-item label="时间颗粒度" prop="forecastFrequency"><el-select v-model="form.forecastFrequency"><el-option label="日" value="DAILY" /><el-option label="旬" value="TENDAY" /><el-option label="月" value="MONTHLY" /></el-select></el-form-item>
          <el-form-item label="启用状态"><el-switch v-model="form.enabled" /></el-form-item>
        </div>
        <el-descriptions v-if="selectedTrainConfig" class="train-config-summary" title="自动获取的训练配置" :column="2" border>
          <el-descriptions-item label="智能体">{{ agentLabel(selectedTrainConfig.agentCode) }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ selectedTrainConfig.modelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="区域">{{ selectedTrainConfig.regionName || '全部' }}</el-descriptions-item>
          <el-descriptions-item label="行业">{{ selectedTrainConfig.industryName || '全部' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ selectedTrainConfig.customerName || '全部' }}</el-descriptions-item>
          <el-descriptions-item label="最新成功批次">自动选择</el-descriptions-item>
        </el-descriptions>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="drawerVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="instancesVisible" :title="`${selectedConfig?.forecastName || ''} - 预测实例`" fullscreen class="instances-dialog" destroy-on-close @opened="renderResultChart">
      <div class="instances-workbench">
        <aside class="instances-sidebar">
          <div class="instance-filter"><strong>预测实例</strong><el-select v-model="recordStatusFilter" clearable placeholder="全部状态" @change="handleRecordFilter"><el-option label="预测中" :value="1" /><el-option label="预测成功" :value="2" /><el-option label="预测失败" :value="3" /></el-select></div>
          <div class="instance-list" v-loading="recordsLoading">
            <button v-for="row in recordRows" :key="row.id" type="button" class="instance-card" :class="{ active: activeRecord?.id === row.id }" @click="selectRecord(row)">
              <div><strong>{{ row.forecastBatchNo }}</strong><el-tag size="small" :type="recordStatus(row.status).type">{{ recordStatus(row.status).label }}</el-tag></div>
              <p>{{ row.forecastStartDate }} · {{ row.forecastFrequency }} · {{ row.forecastHorizon }}步</p>
              <p>发起日期：{{ formatDate(row.createdAt) }}</p>
              <el-button v-if="Number(row.status) === 3" link type="warning" @click.stop="retryRecord(row)">重试预测</el-button>
            </button>
            <el-empty v-if="!recordsLoading && !recordRows.length" description="暂无预测实例" />
          </div>
          <AppPagination v-model:current-page="recordPage" v-model:page-size="recordSize" :total="recordTotal" @current-change="loadRecords" @size-change="loadRecords" />
        </aside>
        <main v-if="activeRecord" class="instance-detail">
          <div class="panel-title"><div><h3>{{ activeRecord.forecastBatchNo }}</h3><p>预测详情</p></div><el-button type="primary" :loading="runningCode === selectedConfig?.id" @click="selectedConfig && openPredict(selectedConfig)">发起预测</el-button></div>
        <h4>基本信息</h4>
        <el-descriptions :column="4" border>
          <el-descriptions-item label="预测批次号">{{ activeRecord.forecastBatchNo }}</el-descriptions-item>
          <el-descriptions-item label="预测状态"><el-tag :type="recordStatus(activeRecord.status).type">{{ recordStatus(activeRecord.status).label }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ activeRecord.forecastEndTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ activeRecord.createdByName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="区域">{{ activeRecord.regionName || '全部' }}</el-descriptions-item>
          <el-descriptions-item label="行业">{{ activeRecord.industryName || '全部' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ activeRecord.customerName || '全部' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ activeRecord.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
          <div class="result-heading"><h4>预测结果</h4><el-radio-group v-model="resultView" size="small" @change="handleResultViewChange"><el-radio-button value="chart">图表</el-radio-button><el-radio-button value="table">列表</el-radio-button></el-radio-group></div>
          <div v-show="resultView === 'chart'" v-loading="resultLoading" ref="resultChartRef" class="result-chart"></div>
          <template v-if="resultView === 'table'">
            <AppTable v-loading="resultLoading" :data="resultRows" border stripe><el-table-column prop="forecastDate" label="预测日期" min-width="160" /><el-table-column prop="forecastValue" label="预测值" min-width="180" align="right" /><el-table-column label="创建日期" width="130"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column></AppTable>
            <AppPagination v-model:current-page="resultPage" v-model:page-size="resultSize" :total="resultTotal" @current-change="loadResultRows" @size-change="loadResultRows" />
          </template>
        </main>
        <el-empty v-else class="instance-detail" description="请选择左侧预测实例" />
      </div>
    </el-dialog>

    <el-dialog v-model="predictVisible" title="填写预测特征数据" width="900px" destroy-on-close align-center>
      <el-alert title="请输入 JSON 数组，每条数据必须包含 date 字段；确认后将作为预测接口的 dataset 参数。" type="info" :closable="false" show-icon />
      <el-input v-model="featureDataJson" class="feature-json-input" type="textarea" :rows="18" resize="vertical" spellcheck="false" placeholder='[{"date":"2026-05-26","未来动态特征":100}]' />
      <template #footer>
        <el-button @click="predictVisible = false">取消</el-button>
        <el-button type="primary" :loading="runningCode !== null" @click="confirmPredict">确认预测</el-button>
      </template>
    </el-dialog>

  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { deleteRow, listPage, postJson } from '@/api/management'
import AppPagination from '@/components/AppPagination.vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'

const agents = [{ label: '冬季保供', value: 'winter-supply' }, { label: '月度销量', value: 'monthly-sales' }, { label: '短期客户', value: 'short-term' }]
const rows = ref<Record<string, any>[]>([]), keyword = ref(''), agentCode = ref(''), loading = ref(false), drawerVisible = ref(false), runningCode = ref<number | null>(null)
const saving = ref(false), formRef = ref<FormInstance>()
const page = ref(1), size = ref(10), total = ref(0)
const recordsLoading = ref(false), recordRows = ref<Record<string, any>[]>([]), selectedConfig = ref<Record<string, any> | null>(null)
const instancesVisible = ref(false)
const recordStatusFilter = ref<number | undefined>()
const predictVisible = ref(false), featureDataJson = ref('[\n  {\n    "date": ""\n  }\n]'), pendingForecast = ref<Record<string, any> | null>(null)
const activeRecord = ref<Record<string, any> | null>(null)
const resultLoading = ref(false), resultRows = ref<Record<string, any>[]>([]), resultPage = ref(1), resultSize = ref(20), resultTotal = ref(0)
const resultView = ref<'chart' | 'table'>('chart'), resultChartRef = ref<HTMLElement>(), chartRows = ref<Record<string, any>[]>([])
let resultChart: echarts.ECharts | null = null
const recordPage = ref(1), recordSize = ref(10), recordTotal = ref(0), currentForecastId = ref<number | null>(null)
const trainConfigOptions = ref<Record<string, any>[]>([])
const emptyForm = () => ({ id: undefined as number | undefined, forecastName: '', agentCode: 'winter-supply', scopeType: 'ALL', regionCode: '', regionName: '', industryCode: '', industryName: '', customerCode: '', customerName: '', trainConfigCode: '', forecastStartDate: '', forecastHorizon: 15, forecastFrequency: 'TENDAY', autoForecast: false, enabled: true, remark: '' })
const form = reactive(emptyForm())
const rules: FormRules = { forecastName: [{ required: true, message: '请输入预测名称', trigger: 'blur' }], trainConfigCode: [{ required: true, message: '请选择模型训练配置', trigger: 'change' }], forecastStartDate: [{ required: true, message: '请选择预测开始日期', trigger: 'change' }], forecastHorizon: [{ required: true, message: '请输入预测步长', trigger: 'change' }], forecastFrequency: [{ required: true, message: '请选择时间颗粒度', trigger: 'change' }] }
const selectedTrainConfig = computed(() => trainConfigOptions.value.find(item => item.trainCode === form.trainConfigCode))
const agentLabel = (code: string) => agents.find(item => item.value === code)?.label || code
const formatDate = (value: unknown) => value ? String(value).slice(0, 10) : '-'
const loadData = async () => { loading.value = true; try { const data = await listPage('/model-forecast-config', { page: page.value, size: size.value, keyword: keyword.value, agentCode: agentCode.value }); rows.value = data.records; total.value = data.total } finally { loading.value = false } }
const loadTrainConfigs = async () => { const configs = await listPage('/model-train-config', { page: 1, size: 1000 }); trainConfigOptions.value = configs.records.filter((item: any) => Number(item.enabled) === 1) }
const handleTrainConfigChange = () => { const item = selectedTrainConfig.value; if (!item) return; form.agentCode = item.agentCode; form.scopeType = item.customerCode ? 'CUSTOMER' : item.industryCode ? 'INDUSTRY' : item.regionCode ? 'REGION' : 'ALL'; form.regionCode = item.regionCode || ''; form.regionName = item.regionName || ''; form.industryCode = item.industryCode || ''; form.industryName = item.industryName || ''; form.customerCode = item.customerCode || ''; form.customerName = item.customerName || ''; const defaults: Record<string, [string, number]> = { 'winter-supply': ['TENDAY', 15], 'monthly-sales': ['MONTHLY', 12], 'short-term': ['DAILY', 30] }; [form.forecastFrequency, form.forecastHorizon] = defaults[item.agentCode] || ['DAILY', 30] }
const prepareDialogOptions = async () => {
  try {
    await loadTrainConfigs()
    if (form.trainConfigCode) handleTrainConfigChange()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '配置选项加载失败')
  } finally {
    formRef.value?.clearValidate()
  }
}
const openCreate = () => { Object.assign(form, emptyForm()); drawerVisible.value = true; void prepareDialogOptions() }
const openEdit = (row: Record<string, any>) => { Object.assign(form, emptyForm(), row, { autoForecast: Number(row.autoForecast) === 1, enabled: Number(row.enabled) === 1 }); drawerVisible.value = true; void prepareDialogOptions() }
const save = async () => { if (!await formRef.value?.validate().catch(() => false)) return; saving.value = true; try { await postJson('/model-forecast-config/save', { ...form, autoForecast: form.autoForecast ? 1 : 0, enabled: form.enabled ? 1 : 0 }); drawerVisible.value = false; ElMessage.success('保存成功'); await loadData() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { saving.value = false } }
const remove = async (row: Record<string, any>) => { await ElMessageBox.confirm(`确认删除配置“${row.forecastName}”？`, '删除确认', { type: 'warning' }); await deleteRow('/model-forecast-config', row.id); ElMessage.success('删除成功'); await loadData() }
const recordStatus = (status: number): { label: string; type: any } => ({ 1: { label: '预测中', type: 'warning' }, 2: { label: '预测成功', type: 'success' }, 3: { label: '预测失败', type: 'danger' } }[Number(status)] || { label: '未知', type: 'info' })
const loadRecords = async () => { if (!currentForecastId.value) return; recordsLoading.value = true; try { const data = await listPage('/model-forecast-record', { page: recordPage.value, size: recordSize.value, forecastId: currentForecastId.value, status: recordStatusFilter.value }); recordRows.value = data.records; recordTotal.value = data.total; if (recordRows.value.length) selectRecord(recordRows.value[0]); else { activeRecord.value = null; resultRows.value = []; resultTotal.value = 0; chartRows.value = []; renderResultChart() } } finally { recordsLoading.value = false } }
const selectConfig = (row: Record<string, any>) => { selectedConfig.value = row; currentForecastId.value = Number(row.id); recordPage.value = 1; void loadRecords() }
const openInstances = (row: Record<string, any>) => { recordStatusFilter.value = undefined; resultView.value = 'chart'; instancesVisible.value = true; selectConfig(row) }
const handleRecordFilter = () => { recordPage.value = 1; void loadRecords() }
const loadResultRows = async () => {
  if (!activeRecord.value?.forecastBatchNo) { resultRows.value = []; resultTotal.value = 0; return }
  resultLoading.value = true
  try {
    const data = await listPage('/model-forecast-result', { page: resultPage.value, size: resultView.value === 'chart' ? 1000 : resultSize.value, forecastBatchNo: activeRecord.value.forecastBatchNo })
    resultRows.value = data.records
    chartRows.value = data.records
    resultTotal.value = data.total
    await nextTick(); renderResultChart()
  } finally { resultLoading.value = false }
}
const selectRecord = (row?: Record<string, any>) => { if (!row) return; activeRecord.value = row; resultPage.value = 1; void loadResultRows() }
const renderResultChart = () => {
  if (!resultChartRef.value || resultView.value !== 'chart') return
  resultChart = echarts.getInstanceByDom(resultChartRef.value) || echarts.init(resultChartRef.value)
  const data = [...chartRows.value].sort((a, b) => String(a.forecastDate).localeCompare(String(b.forecastDate)))
  resultChart.setOption({ tooltip: { trigger: 'axis' }, grid: { left: 58, right: 28, top: 35, bottom: 55 }, xAxis: { type: 'category', data: data.map(item => item.forecastDate), axisLabel: { rotate: data.length > 15 ? 35 : 0 } }, yAxis: { type: 'value', name: '预测值', scale: true }, dataZoom: data.length > 20 ? [{ type: 'inside' }, { type: 'slider', bottom: 5 }] : [], series: [{ name: '预测值', type: 'line', smooth: true, showSymbol: data.length < 50, data: data.map(item => Number(item.forecastValue)), areaStyle: { opacity: .08 }, lineStyle: { width: 2 } }] }, true)
}
const handleResultViewChange = () => { resultPage.value = 1; void loadResultRows() }
const openPredict = (row: Record<string, any>) => { pendingForecast.value = row; featureDataJson.value = '[\n  {\n    "date": ""\n  }\n]'; predictVisible.value = true }
const confirmPredict = async () => {
  let dataset: unknown
  try { dataset = JSON.parse(featureDataJson.value) } catch { ElMessage.error('特征数据不是有效的 JSON'); return }
  if (!Array.isArray(dataset) || dataset.length === 0) { ElMessage.error('特征数据必须是非空 JSON 数组'); return }
  if (dataset.some(item => !item || typeof item !== 'object' || !String((item as Record<string, unknown>).date || '').trim())) { ElMessage.error('每条特征数据都必须包含 date 字段'); return }
  if (dataset.some(item => !/^\d{4}-\d{2}-\d{2}$/.test(String((item as Record<string, unknown>).date)))) { ElMessage.error('date 格式必须为 YYYY-MM-DD'); return }
  const row = pendingForecast.value
  if (!row) return
  const forecastId = Number(row.forecastId || row.id)
  runningCode.value = forecastId
  try {
    const response = await postJson('/model-forecast-execution/execute', { forecastId, dataset })
    const data = response.data || {}
    predictVisible.value = false
    ElMessage.success(`预测成功：${data.resultCount || 0} 条结果`)
    if (currentForecastId.value === forecastId) await loadRecords()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预测失败')
    if (currentForecastId.value === forecastId) await loadRecords()
  } finally { runningCode.value = null }
}
const retryRecord = (row: Record<string, any>) => openPredict(row)
onMounted(loadData)
onBeforeUnmount(() => resultChart?.dispose())
</script>

<style scoped>
.forecast-page { display: flex; flex-direction: column; gap: 14px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.train-config-summary { margin: 4px 0 18px; }
:deep(.forecast-config-dialog .el-dialog__body) { max-height: 68vh; overflow-y: auto; padding-top: 12px; }
:deep(.forecast-config-dialog .el-select), :deep(.forecast-config-dialog .el-date-editor), :deep(.forecast-config-dialog .el-input-number) { width: 100%; }
.feature-json-input { margin-top: 16px; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.panel-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.panel-title h3 { margin: 0; font-size: 18px; }
.panel-title p { margin: 4px 0 0; color: var(--el-text-color-secondary); }
.inline-detail { margin-top: 20px; padding-top: 18px; border-top: 1px solid var(--el-border-color-lighter); }
.inline-detail h4 { margin: 0 0 12px; font-size: 16px; }
.inline-detail h4:not(:first-child) { margin-top: 20px; }
:deep(.instances-dialog .el-dialog__body) { height: calc(100vh - 58px); padding: 0; overflow: hidden; }
.instances-workbench { display: grid; grid-template-columns: 350px minmax(0, 1fr); height: 100%; background: #f5f7fa; }
.instances-sidebar { display: flex; flex-direction: column; min-height: 0; padding: 16px; background: #fff; border-right: 1px solid var(--el-border-color-lighter); }
.instance-filter { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.instance-filter .el-select { width: 140px; }
.instance-list { flex: 1; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; }
.instance-card { width: 100%; padding: 12px; border: 1px solid var(--el-border-color); border-radius: 7px; background: #fff; text-align: left; cursor: pointer; }
.instance-card:hover, .instance-card.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.instance-card > div { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.instance-card p { margin: 7px 0 0; color: var(--el-text-color-secondary); font-size: 12px; }
.instance-detail { min-width: 0; overflow-y: auto; padding: 20px 24px 36px; }
.instance-detail h4 { margin: 18px 0 12px; }
.result-heading { display: flex; align-items: center; justify-content: space-between; margin-top: 6px; }
.result-heading h4 { margin: 18px 0 12px; }
.result-chart { width: 100%; height: 430px; background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
@media (max-width: 800px) {
  .form-grid { grid-template-columns: 1fr; }
  .instances-workbench { grid-template-columns: 1fr; overflow-y: auto; }
  .instances-sidebar { min-height: 380px; border-right: 0; border-bottom: 1px solid var(--el-border-color-lighter); }
  .instance-detail { overflow: visible; }
  :deep(.forecast-config-dialog) { width: calc(100vw - 24px) !important; }
}
</style>
