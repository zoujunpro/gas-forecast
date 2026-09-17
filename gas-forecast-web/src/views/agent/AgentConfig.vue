<template>
  <section class="config-page">
    <div class="page-heading-wrap">
      <PageBreadcrumb />
    </div>

    <AppTablePanel>
      <template #filters>
          <el-input v-model="keyword" class="keyword-input" clearable placeholder="搜索配置名称、编码" :prefix-icon="Search" @keyup.enter="searchConfigs" />
          <el-select v-model="statusFilter" class="status-select" placeholder="状态">
            <el-option label="全部状态" value="ALL" />
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="searchConfigs">查询</el-button>
          <el-button type="info" plain @click="resetFilters">重置</el-button>
      </template>
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建配置</el-button>
      </template>
      <AppTable :data="filteredConfigs" class="config-table" border stripe>
        <el-table-column type="index" label="序号" width="72" fixed class-name="id-column" label-class-name="id-column" />
        <el-table-column prop="configCode" label="配置编码" min-width="150" />
        <el-table-column prop="configName" label="配置名称" min-width="180" />
        <el-table-column label="作用范围" min-width="170">
          <template #default="{ row }">
            {{ formatScope(row) }}
          </template>
        </el-table-column>
        <el-table-column :label="configType === 'train' ? '执行配置' : '预测频率'" min-width="130">
          <template #default="{ row }">
            {{ formatFrequency(row) }}
          </template>
        </el-table-column>
        <el-table-column v-if="configType === 'train'" prop="trainRange" label="训练数据范围" min-width="190" />
        <el-table-column v-else prop="forecastInfo" label="预测设置" min-width="180" />
        <el-table-column :label="configType === 'train' ? '执行方式' : '自动执行'" width="110">
          <template #default="{ row }">
            <el-tag :type="row.autoRun ? 'success' : 'info'" effect="plain">
              {{ configType === 'train' ? (row.autoRun ? '定时' : '手动') : (row.autoRun ? '是' : '否') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" inline-prompt active-text="启" inactive-text="停" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="最近任务" min-width="190">
          <template #default="{ row }">
            <span v-if="row.lastTaskCode" class="task-cell">
              <el-tag size="small" type="warning" effect="plain">占位</el-tag>
              <span>{{ row.lastTaskCode }}</span>
            </span>
            <span v-else class="muted-text">未执行</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right" class-name="action-column" label-class-name="action-column">
          <template #default="{ row }">
            <el-button link type="success" @click="runConfig(row)">执行</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeConfig(row.configCode)">删除</el-button>
          </template>
        </el-table-column>
      </AppTable>
      <template #footer>
        <div class="table-summary">共 {{ filteredConfigs.length }} 条数据</div>
      </template>
    </AppTablePanel>

    <el-drawer v-model="drawerVisible" class="config-drawer" :title="drawerTitle" size="620px">
      <el-form class="config-form" label-position="top" :model="form">
        <el-form-item label="配置名称">
          <el-input v-model="form.configName" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="执行范围">
          <el-segmented v-model="form.scopeMode" :options="scopeModeOptions" />
        </el-form-item>
        <div v-if="form.scopeMode === 'PARTIAL'" class="scope-fields">
          <el-form-item label="区域">
            <el-select v-model="form.regionName" clearable multiple placeholder="选择区域">
              <el-option v-for="region in regions" :key="region" :label="region" :value="region" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="agentCode !== 'winter-supply'" label="行业">
            <el-select v-model="form.industryName" clearable multiple placeholder="选择行业">
              <el-option v-for="industry in industries" :key="industry" :label="industry" :value="industry" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="agentCode === 'short-term'" label="客户">
            <el-select v-model="form.customerName" clearable multiple placeholder="选择客户">
              <el-option v-for="customer in customers" :key="customer" :label="customer" :value="customer" />
            </el-select>
          </el-form-item>
        </div>

        <template v-if="configType === 'train'">
          <div class="form-grid">
            <el-form-item :label="trainWindowLabel">
              <el-input-number v-model="form.trainWindowSize" :min="1" controls-position="right" />
            </el-form-item>
            <el-form-item label="执行方式">
              <el-segmented v-model="form.executionMode" :options="executionModeOptions" />
            </el-form-item>
          </div>
          <div v-if="form.executionMode === 'SCHEDULED'" class="form-grid">
            <el-form-item label="定时频率">
              <el-select v-model="form.frequency">
                <el-option label="每日" value="DAILY" />
                <el-option label="每周" value="WEEKLY" />
                <el-option label="每旬" value="TENDAY" />
                <el-option label="每月" value="MONTHLY" />
                <el-option label="每季" value="SEASONAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="执行时间">
              <el-time-picker v-model="form.executionTime" format="HH:mm" value-format="HH:mm" placeholder="选择时间" />
            </el-form-item>
          </div>
          <div v-if="form.executionMode === 'SCHEDULED' && form.frequency !== 'DAILY'" class="form-grid">
            <el-form-item v-if="form.frequency === 'WEEKLY'" label="每周第几天">
              <el-select v-model="form.weekday">
                <el-option v-for="day in weekdayOptions" :key="day.value" :label="day.label" :value="day.value" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.frequency === 'TENDAY'" label="每旬第几段">
              <el-select v-model="form.tendayPart">
                <el-option label="上旬" value="EARLY" />
                <el-option label="中旬" value="MIDDLE" />
                <el-option label="下旬" value="LATE" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.frequency === 'MONTHLY' || form.frequency === 'SEASONAL'" label="每月第几天">
              <el-input-number v-model="form.dayOfMonth" :min="1" :max="31" controls-position="right" />
            </el-form-item>
            <el-form-item v-if="form.frequency === 'SEASONAL'" label="季度第几个月">
              <el-select v-model="form.quarterMonth">
                <el-option label="第 1 个月" :value="1" />
                <el-option label="第 2 个月" :value="2" />
                <el-option label="第 3 个月" :value="3" />
              </el-select>
            </el-form-item>
          </div>
        </template>

        <template v-else>
          <div class="form-grid">
            <el-form-item label="预测开始日期">
              <el-date-picker v-model="form.forecastStartDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="预测步长">
              <el-input-number v-model="form.forecastHorizon" :min="1" controls-position="right" />
            </el-form-item>
          </div>
          <div class="form-grid">
            <el-form-item label="预测频率">
              <el-select v-model="form.frequency">
                <el-option label="每日" value="DAILY" />
                <el-option label="每周" value="WEEKLY" />
                <el-option label="每月" value="MONTHLY" />
                <el-option label="每季" value="SEASONAL" />
                <el-option label="手动" value="MANUAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="气象来源">
              <el-select v-model="form.weatherSource">
                <el-option label="用户录入" value="manual" />
                <el-option label="气象接口" value="api" />
                <el-option label="历史同期" value="history" />
              </el-select>
            </el-form-item>
          </div>
        </template>

        <div class="form-grid">
          <el-form-item v-if="configType === 'forecast'" label="自动执行">
            <el-switch v-model="form.autoRun" />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </div>
        <el-form-item class="full-row" label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button type="info" plain @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" @click="saveConfig">保存</el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'

type ConfigType = 'train' | 'forecast'
type ScopeMode = 'ALL' | 'PARTIAL'
type ExecutionMode = 'MANUAL' | 'SCHEDULED'

interface ConfigRow {
  configCode: string
  configName: string
  scopeMode: ScopeMode
  regionName?: string
  industryName?: string
  customerName?: string
  frequency: string
  executionTime?: string
  weekday?: number
  tendayPart?: string
  dayOfMonth?: number
  quarterMonth?: number
  trainRange?: string
  forecastInfo?: string
  autoRun: boolean
  enabled: boolean
  updatedAt: string
  remark?: string
  lastTaskCode?: string
  lastTaskAt?: string
}

interface ConfigForm {
  configCode: string
  configName: string
  scopeMode: ScopeMode
  regionName: string[]
  industryName: string[]
  customerName: string[]
  trainWindowSize: number
  forecastStartDate: string
  forecastHorizon: number
  frequency: string
  executionMode: ExecutionMode
  executionTime: string
  weekday: number
  tendayPart: string
  dayOfMonth: number
  quarterMonth: number
  weatherSource: string
  autoRun: boolean
  enabled: boolean
  remark: string
}

const route = useRoute()
const keyword = ref('')
const statusFilter = ref('ALL')
const appliedKeyword = ref('')
const appliedStatus = ref('ALL')
const drawerVisible = ref(false)
const editingCode = ref('')

const regions = ['华北区域', '北京', '天津', '河北', '山东', '山西', '河南', '陕西']
const industries = ['城燃', '工业', '电厂']
const customers = ['全部客户', '重点客户A', '重点客户B', '重点客户C']

const agentLabelMap: Record<string, string> = {
  'winter-supply': '冬季保供',
  'monthly-sales': '月度销量',
  'short-term': '短期客户'
}

const configType = computed<ConfigType>(() => route.meta.configType === 'train' ? 'train' : 'forecast')
const agentCode = computed(() => String(route.meta.agentId || 'winter-supply'))
const agentLabel = computed(() => agentLabelMap[agentCode.value] ?? '智能体')
const pageLabel = computed(() => configType.value === 'train' ? '训练配置' : '预测配置')
const pageTitle = computed(() => `${agentLabel.value}${pageLabel.value}`)
const drawerTitle = computed(() => editingCode.value ? `编辑${pageLabel.value}` : `新建${pageLabel.value}`)
const trainWindowUnit = computed(() => {
  if (agentCode.value === 'winter-supply') {
    return '旬'
  }
  if (agentCode.value === 'short-term') {
    return '天'
  }
  return '月'
})
const trainWindowLabel = computed(() => `最近${trainWindowUnit.value}数`)
const scopeModeOptions = [
  { label: '全部', value: 'ALL' },
  { label: '指定范围', value: 'PARTIAL' }
]
const executionModeOptions = [
  { label: '手动', value: 'MANUAL' },
  { label: '定时', value: 'SCHEDULED' }
]
const weekdayOptions = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 }
]

const form = reactive<ConfigForm>({
  configCode: '',
  configName: '',
  scopeMode: 'ALL',
  regionName: [],
  industryName: [],
  customerName: [],
  trainWindowSize: 36,
  forecastStartDate: '2026-09-01',
  forecastHorizon: 12,
  frequency: 'MONTHLY',
  executionMode: 'SCHEDULED',
  executionTime: '02:00',
  weekday: 1,
  tendayPart: 'EARLY',
  dayOfMonth: 1,
  quarterMonth: 1,
  weatherSource: 'manual',
  autoRun: true,
  enabled: true,
  remark: ''
})

const configs = ref<ConfigRow[]>([])

const buildDefaultRows = (): ConfigRow[] => {
  const prefix = configType.value === 'train' ? 'TRAIN' : 'FC'
  const agent = agentCode.value.toUpperCase().replace(/-/g, '_')
  const isTrain = configType.value === 'train'
  const frequency = getDefaultFrequency()

  return [
    {
      configCode: `${prefix}-${agent}-001`,
      configName: `${agentLabel.value}默认${pageLabel.value}`,
      scopeMode: 'ALL',
      frequency,
      executionTime: isTrain ? '02:00' : undefined,
      weekday: isTrain && frequency === 'WEEKLY' ? 1 : undefined,
      tendayPart: isTrain && frequency === 'TENDAY' ? 'EARLY' : undefined,
      dayOfMonth: isTrain && ['MONTHLY', 'SEASONAL'].includes(frequency) ? 1 : undefined,
      quarterMonth: isTrain && frequency === 'SEASONAL' ? 1 : undefined,
      trainRange: isTrain ? `最近 ${getDefaultTrainWindow()} ${trainWindowUnit.value}数据` : undefined,
      forecastInfo: isTrain ? undefined : agentCode.value === 'winter-supply' ? '2026-11-01 起 15 旬' : '2026-09-01 起 12 期',
      autoRun: true,
      enabled: true,
      updatedAt: '2026-09-10 13:20'
    },
    {
      configCode: `${prefix}-${agent}-002`,
      configName: `${agentLabel.value}人工复核配置`,
      scopeMode: 'PARTIAL',
      regionName: '北京',
      industryName: agentCode.value === 'winter-supply' ? undefined : '城燃',
      customerName: agentCode.value === 'short-term' ? '重点客户A' : undefined,
      frequency: 'MANUAL',
      executionTime: undefined,
      trainRange: isTrain ? `最近 ${getManualTrainWindow()} ${trainWindowUnit.value}数据` : undefined,
      forecastInfo: isTrain ? undefined : '手动选择预测周期',
      autoRun: false,
      enabled: false,
      updatedAt: '2026-09-09 16:45'
    }
  ]
}

watch(
  () => [configType.value, agentCode.value],
  () => {
    configs.value = buildDefaultRows()
    keyword.value = ''
    statusFilter.value = 'ALL'
    appliedKeyword.value = ''
    appliedStatus.value = 'ALL'
  },
  { immediate: true }
)

const filteredConfigs = computed(() => configs.value.filter((item) => {
  const searchText = appliedKeyword.value.trim()
  const hitKeyword = !searchText || [item.configCode, item.configName].some((value) => value.includes(searchText))
  const hitStatus = appliedStatus.value === 'ALL' || (appliedStatus.value === 'ENABLED' ? item.enabled : !item.enabled)
  return hitKeyword && hitStatus
}))

const searchConfigs = () => {
  appliedKeyword.value = keyword.value
  appliedStatus.value = statusFilter.value
}

const resetFilters = () => {
  keyword.value = ''
  statusFilter.value = 'ALL'
  appliedKeyword.value = ''
  appliedStatus.value = 'ALL'
}

const formatScope = (row: ConfigRow) => {
  if (row.scopeMode === 'ALL') {
    return '全部'
  }
  return [row.regionName, row.industryName, row.customerName].filter(Boolean).join(' / ') || '指定范围'
}

const frequencyLabelMap: Record<string, string> = {
  DAILY: '每日',
  WEEKLY: '每周',
  TENDAY: '每旬',
  MONTHLY: '每月',
  SEASONAL: '每季',
  MANUAL: '手动'
}

const tendayLabelMap: Record<string, string> = {
  EARLY: '上旬',
  MIDDLE: '中旬',
  LATE: '下旬'
}

const weekdayLabelMap = Object.fromEntries(weekdayOptions.map((item) => [item.value, item.label])) as Record<number, string>

const formatFrequency = (row: ConfigRow) => {
  const label = frequencyLabelMap[row.frequency] || row.frequency
  if (configType.value !== 'train' || row.frequency === 'MANUAL') {
    return label
  }

  const time = row.executionTime || '02:00'
  if (row.frequency === 'WEEKLY') {
    return `每周${weekdayLabelMap[row.weekday || 1]} ${time}`
  }
  if (row.frequency === 'TENDAY') {
    return `每旬${tendayLabelMap[row.tendayPart || 'EARLY']} ${time}`
  }
  if (row.frequency === 'MONTHLY') {
    return `每月${row.dayOfMonth || 1}日 ${time}`
  }
  if (row.frequency === 'SEASONAL') {
    return `每季第${row.quarterMonth || 1}个月${row.dayOfMonth || 1}日 ${time}`
  }
  return `${label} ${time}`
}

function getCurrentTime() {
  return new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

function getDefaultTrainWindow() {
  if (agentCode.value === 'winter-supply') {
    return 36
  }
  if (agentCode.value === 'short-term') {
    return 90
  }
  return 36
}

function getManualTrainWindow() {
  if (agentCode.value === 'winter-supply') {
    return 24
  }
  if (agentCode.value === 'short-term') {
    return 30
  }
  return 24
}

function getDefaultFrequency(): string {
  if (agentCode.value === 'short-term') {
    return 'DAILY'
  }
  if (agentCode.value === 'winter-supply') {
    return configType.value === 'train' ? 'TENDAY' : 'SEASONAL'
  }
  return 'MONTHLY'
}

function parseTrainWindowSize(trainRange?: string) {
  const matched = trainRange?.match(/\d+/)
  return matched ? Number(matched[0]) : getDefaultTrainWindow()
}

const resetForm = () => {
  form.configCode = ''
  form.configName = ''
  form.scopeMode = 'ALL'
  form.regionName = []
  form.industryName = []
  form.customerName = []
  form.trainWindowSize = getDefaultTrainWindow()
  form.forecastStartDate = agentCode.value === 'winter-supply' ? '2026-11-01' : '2026-09-01'
  form.forecastHorizon = agentCode.value === 'winter-supply' ? 15 : agentCode.value === 'short-term' ? 30 : 12
  form.frequency = getDefaultFrequency()
  form.executionMode = 'SCHEDULED'
  form.executionTime = '02:00'
  form.weekday = 1
  form.tendayPart = 'EARLY'
  form.dayOfMonth = 1
  form.quarterMonth = 1
  form.weatherSource = 'manual'
  form.autoRun = true
  form.enabled = true
  form.remark = ''
}

const openCreate = () => {
  editingCode.value = ''
  resetForm()
  drawerVisible.value = true
}

const openEdit = (row: ConfigRow) => {
  editingCode.value = row.configCode
  resetForm()
  form.configCode = row.configCode
  form.configName = row.configName
  form.scopeMode = row.scopeMode
  form.regionName = row.regionName ? row.regionName.split('、') : []
  form.industryName = row.industryName ? row.industryName.split('、') : []
  form.customerName = row.customerName ? row.customerName.split('、') : []
  form.trainWindowSize = parseTrainWindowSize(row.trainRange)
  form.frequency = row.frequency
  form.executionMode = row.frequency === 'MANUAL' ? 'MANUAL' : 'SCHEDULED'
  form.executionTime = row.executionTime || '02:00'
  form.weekday = row.weekday || 1
  form.tendayPart = row.tendayPart || 'EARLY'
  form.dayOfMonth = row.dayOfMonth || 1
  form.quarterMonth = row.quarterMonth || 1
  form.autoRun = row.autoRun
  form.enabled = row.enabled
  form.remark = row.remark || ''
  drawerVisible.value = true
}

const saveConfig = () => {
  const code = editingCode.value || `${configType.value === 'train' ? 'TRAIN' : 'FC'}-${Date.now()}`
  const isTrain = configType.value === 'train'
  const frequency = isTrain && form.executionMode === 'MANUAL' ? 'MANUAL' : form.frequency
  const row: ConfigRow = {
    configCode: code,
    configName: form.configName || pageTitle.value,
    scopeMode: form.scopeMode,
    regionName: form.scopeMode === 'PARTIAL' ? form.regionName.join('、') : undefined,
    industryName: form.scopeMode === 'PARTIAL' ? form.industryName.join('、') : undefined,
    customerName: form.scopeMode === 'PARTIAL' ? form.customerName.join('、') : undefined,
    frequency,
    executionTime: isTrain && form.executionMode === 'SCHEDULED' ? form.executionTime : undefined,
    weekday: isTrain && frequency === 'WEEKLY' ? form.weekday : undefined,
    tendayPart: isTrain && frequency === 'TENDAY' ? form.tendayPart : undefined,
    dayOfMonth: isTrain && ['MONTHLY', 'SEASONAL'].includes(frequency) ? form.dayOfMonth : undefined,
    quarterMonth: isTrain && frequency === 'SEASONAL' ? form.quarterMonth : undefined,
    trainRange: isTrain ? `最近 ${form.trainWindowSize} ${trainWindowUnit.value}数据` : undefined,
    forecastInfo: configType.value === 'forecast' ? `${form.forecastStartDate} 起 ${form.forecastHorizon} 期` : undefined,
    autoRun: isTrain ? form.executionMode === 'SCHEDULED' : form.autoRun,
    enabled: form.enabled,
    updatedAt: getCurrentTime(),
    remark: form.remark
  }

  const index = configs.value.findIndex((item) => item.configCode === code)
  if (index >= 0) {
    configs.value.splice(index, 1, row)
  } else {
    configs.value.unshift(row)
  }
  drawerVisible.value = false
}

const removeConfig = (configCode: string) => {
  configs.value = configs.value.filter((item) => item.configCode !== configCode)
}

const runConfig = (row: ConfigRow) => {
  const actionName = configType.value === 'train' ? '训练任务' : '预测任务'
  const batchPrefix = configType.value === 'train' ? 'TRAIN' : 'FC'
  const taskCode = `${batchPrefix}-TASK-${Date.now()}`

  row.lastTaskCode = taskCode
  row.lastTaskAt = getCurrentTime()
  row.updatedAt = row.lastTaskAt
  ElMessage.success(`已创建${actionName}占位任务：${taskCode}，来源配置 ${row.configCode}`)
}
</script>

<style scoped>
.config-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-heading-wrap {
  padding: 0 4px;
}

.filter-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.keyword-input {
  width: 320px;
}

.status-select {
  width: 132px;
}

.result-count {
  color: #667085;
  font-size: 13px;
}

.table-summary {
  color: #667085;
  font-size: 13px;
  padding-top: 14px;
}

.config-table :deep(.el-table__header th) {
  background: #F8FAFC;
  color: #344054;
  font-weight: 700;
}

.config-table :deep(.el-table__cell) {
  padding: 10px 0;
}

.config-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
  row-gap: 2px;
  padding-right: 4px;
}

.form-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
  row-gap: 2px;
}

.scope-fields {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: 16px;
  row-gap: 2px;
}

.full-row {
  grid-column: 1 / -1;
}

.form-grid :deep(.el-date-editor),
.form-grid :deep(.el-input-number),
.form-grid :deep(.el-time-picker),
.form-grid :deep(.el-select),
.scope-fields :deep(.el-select),
.config-form :deep(.el-input),
.config-form :deep(.el-select),
.config-form :deep(.el-segmented) {
  width: 100%;
}

.config-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.config-form :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: #344054;
  font-weight: 600;
}

:global(.config-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 22px;
  border-bottom: 1px solid #E6EAF0;
  color: #101828;
  font-weight: 700;
}

:global(.config-drawer .el-drawer__body) {
  padding: 20px 22px 8px;
}

:global(.config-drawer .el-drawer__footer) {
  padding: 14px 22px 18px;
  border-top: 1px solid #E6EAF0;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.task-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.muted-text {
  color: #98A2B3;
}

@media (max-width: 820px) {
  .keyword-input,
  .status-select {
    width: 100%;
  }

  .config-form,
  .form-grid,
  .scope-fields {
    grid-template-columns: 1fr;
  }
}
</style>
