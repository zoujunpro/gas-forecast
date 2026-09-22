<template>
  <section class="model-page">
    <PageBreadcrumb />

    <AppTablePanel>
      <template #filters>
        <el-input
          v-model="keyword"
          class="search-input"
          clearable
          :prefix-icon="Search"
          placeholder="搜索模型编码、名称、智能体、场景"
          @clear="searchData"
          @keyup.enter="searchData"
        />
        <el-button type="primary" :icon="Search" @click="searchData">查询</el-button>
        <el-button type="info" plain @click="resetSearch">重置</el-button>
      </template>

      <template #actions>
        <PermissionButton type="primary" :icon="Plus" permission="model:config:create" @click="openCreate">新增模型</PermissionButton>
      </template>

      <AppTable v-loading="loading" :data="records" stripe border>
        <el-table-column type="index" :index="rowIndex" label="序号" width="72" fixed class-name="id-column" label-class-name="id-column" />
        <el-table-column
          v-for="field in modelConfigTableFields"
          :key="field.prop"
          :prop="field.prop"
          :label="field.label"
          :min-width="field.minWidth || 120"
          :width="field.width"
          :align="field.align"
          show-overflow-tooltip
        >
          <template #header>
            <el-tooltip v-if="field.tooltip" :content="field.tooltip" placement="top">
              <span class="column-header-with-tip">{{ field.label }}</span>
            </el-tooltip>
            <span v-else>{{ field.label }}</span>
          </template>
          <template #default="{ row }">
            <ManagementTableCell :field="field" :row="row" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" class-name="action-column" label-class-name="action-column">
          <template #default="{ row }">
            <PermissionButton link type="primary" :icon="Edit" permission="model:config:update" @click="openEdit(row)">编辑</PermissionButton>
            <PermissionButton link type="success" :icon="Setting" permission="model:config:update" @click="openConfig(row)">配置</PermissionButton>
            <PermissionButton link type="danger" :icon="Delete" permission="model:config:delete" @click="removeRow(row)">删除</PermissionButton>
          </template>
        </el-table-column>
      </AppTable>

      <template #footer>
        <AppPagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </template>
    </AppTablePanel>

    <AppDialog v-model="dialogVisible" eyebrow="模型管理" :title="dialogTitle" width="min(960px, calc(100vw - 32px))" align-center>
      <el-form ref="formRef" class="dialog-form" :model="form" :rules="formRules" label-position="top">
        <el-form-item label="模型编码" prop="configCode">
          <el-input v-model="form.configCode" clearable maxlength="64" placeholder="请输入模型编码" />
        </el-form-item>
        <el-form-item label="模型名称" prop="configName">
          <el-input v-model="form.configName" clearable maxlength="128" />
        </el-form-item>
        <el-form-item label="模型版本" prop="modelVersion">
          <el-input v-model="form.modelVersion" clearable maxlength="32" placeholder="例如 V1.0" />
        </el-form-item>
        <el-form-item label="所属智能体" prop="agentCode">
          <el-select v-model="form.agentCode" class="form-control" @change="handleAgentChange">
            <el-option v-for="item in modelAgentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="form-wide" label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="info" plain @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveData">保存</el-button>
      </template>
    </AppDialog>

    <AppDialog v-model="configDialogVisible" eyebrow="模型配置" :title="configDialogTitle" width="min(1080px, calc(100vw - 32px))" align-center>
      <div class="config-overview">
        <div class="model-avatar">
          <el-icon><Setting /></el-icon>
        </div>
        <div class="model-title">
          <div class="model-title-row">
            <strong>{{ configForm.configName || '模型配置' }}</strong>
            <el-tag type="success" effect="light">启用</el-tag>
          </div>
          <span>基于气象、能源价格等多源特征，预测冬季各地区天然气需求。</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">模型编码</span>
          <strong>{{ configForm.configCode || '-' }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">所属智能体</span>
          <strong>{{ configForm.agentName || '-' }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">模型版本</span>
          <strong>{{ configForm.modelVersion || '-' }}</strong>
        </div>
      </div>

      <el-form class="config-form" :model="configForm" label-position="top">
        <section class="config-section">
          <header class="section-header">
            <div class="section-title">
              <el-icon><Location /></el-icon>
              <div>
                <h3>适用范围</h3>
                <span>选择该模型适用的地区，可选择多个地区，或选择所有地区</span>
              </div>
            </div>
          </header>
          <div class="scope-row">
            <el-radio-group v-model="regionScopeMode" @change="handleRegionScopeModeChange">
              <el-radio value="CUSTOM">指定地区</el-radio>
              <el-radio value="ALL">所有地区</el-radio>
            </el-radio-group>
          </div>
          <el-form-item label="地区" required>
            <el-select
              v-model="configForm.regionCodes"
              class="form-control"
              multiple
              filterable
              placeholder="请选择地区"
              @change="handleRegionCodesChange"
            >
              <el-option v-for="item in regionOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </section>

        <section class="config-section">
          <header class="section-header">
            <div class="section-title">
              <el-icon><Histogram /></el-icon>
              <div>
                <h3>输入特征</h3>
                <span>选择模型训练需要的输入特征</span>
              </div>
            </div>
            <div class="feature-actions">
              <el-tag class="selected-count" effect="plain">已选 {{ selectedFeatureCount }}</el-tag>
              <el-button plain :disabled="!configForm.featureRefs.length" @click="clearSelectedFeatures">清空选择</el-button>
            </div>
          </header>
          <div class="feature-picker-panel">
            <div class="feature-picker-head">
              <span>特征列表</span>
            </div>
            <el-table
              v-loading="featureLoading"
              class="feature-select-table"
              :data="featureOptions"
              row-key="id"
              border
              max-height="360"
              empty-text="暂无特征"
              :row-class-name="featureRowClassName"
              @row-click="toggleFeatureSelection"
            >
              <el-table-column label="" width="48" align="center">
                <template #default="{ row }">
                  <el-checkbox :model-value="isFeatureSelected(row)" @click.stop @change="toggleFeatureSelection(row)" />
                </template>
              </el-table-column>
              <el-table-column type="index" label="序号" width="64" />
              <el-table-column prop="featureName" label="特征名称" min-width="160" show-overflow-tooltip />
              <el-table-column prop="featureColumn" label="特征字段" min-width="150" show-overflow-tooltip />
              <el-table-column label="时间跨度" width="110">
                <template #default="{ row }">
                  <el-tag effect="plain">{{ timeGranularityText(row.timeGranularity) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="featureCode" label="特征编号" min-width="140" show-overflow-tooltip />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag v-if="isFeatureSelected(row)" type="success" effect="light">已选中</el-tag>
                  <el-tag v-else type="info" effect="plain">未选择</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>
      </el-form>

      <template #footer>
        <el-button type="info" plain @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="saveConfig">保存配置</el-button>
      </template>
    </AppDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Histogram, Location, Plus, Search, Setting } from '@element-plus/icons-vue'
import { createRow, deleteRow, listPage, postJson, updateRow } from '@/api/management'
import { usePageQuery } from '@/composables/usePageQuery'
import AppDialog from '@/components/AppDialog.vue'
import AppPagination from '@/components/AppPagination.vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import ManagementTableCell from '@/components/ManagementTableCell.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { modelAgentOptions, modelConfigTableFields } from './modelManagementConfigs'

defineOptions({ name: 'ModelConfigManagement' })

const endpoint = '/model-config'
const formRef = ref<FormInstance>()
const saving = ref(false)
const configSaving = ref(false)
const dialogVisible = ref(false)
const configDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const configuringId = ref<number | null>(null)
const form = reactive({
  configCode: '',
  configName: '',
  modelVersion: 'V1.0',
  agentCode: 'winter-supply',
  sceneCode: 'WINTER_SUPPLY',
  description: ''
})
const configForm = reactive({
  configCode: '',
  configName: '',
  modelVersion: '',
  agentName: '',
  sceneCode: '',
  regionCodes: ['ALL'] as string[],
  featureRefs: [] as FeatureRefRow[]
})

interface SelectOption {
  label: string
  value: string
}

interface FeatureOption {
  id: number
  featureCode: string
  featureName: string
  featureColumn: string
  timeGranularity: string
}

interface FeatureRefRow extends FeatureOption {
  featureId: number
  requiredFlag: number
  featureOrder: number
}

const regionOptions = ref<SelectOption[]>([])
const featureOptions = ref<FeatureOption[]>([])
const featureLoading = ref(false)
const regionScopeMode = ref<'ALL' | 'CUSTOM'>('ALL')

const formRules: FormRules = {
  configCode: [{ required: true, message: '请输入模型编码', trigger: 'blur' }],
  configName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  agentCode: [{ required: true, message: '请选择所属智能体', trigger: 'change' }],
  sceneCode: [{ required: true, message: '请选择场景', trigger: 'change' }]
}

const {
  loading,
  keyword,
  page,
  size,
  total,
  records,
  loadData,
  searchData,
  resetSearch,
  handleSizeChange,
  rowIndex
} = usePageQuery<Record<string, any>>({
  errorMessage: '模型列表加载失败',
  fetcher: ({ page, size, keyword }) => listPage(endpoint, { page, size, keyword: keyword || undefined })
})

const dialogTitle = computed(() => editingId.value ? '编辑模型' : '新增模型')
const configDialogTitle = computed(() => `${configForm.configName || '模型'}配置`)
const selectedAgent = computed(() => modelAgentOptions.find((item) => item.value === form.agentCode))
const selectedFeatureCount = computed(() => configForm.featureRefs.length)
const syncSceneCode = () => {
  form.sceneCode = selectedAgent.value?.sceneCode || ''
}

const handleAgentChange = () => {
  syncSceneCode()
}

const resetForm = (row?: Record<string, any>) => {
  editingId.value = row?.id ?? null
  form.configCode = row?.configCode || ''
  form.configName = row?.configName || ''
  form.modelVersion = row?.modelVersion || 'V1.0'
  form.agentCode = row?.agentCode || 'winter-supply'
  form.sceneCode = row?.sceneCode || selectedAgent.value?.sceneCode || 'WINTER_SUPPLY'
  form.description = row?.description || ''
  syncSceneCode()
}

const resetConfigForm = (row: Record<string, any>) => {
  configuringId.value = row.id
  configForm.configCode = row.configCode || ''
  configForm.configName = row.configName || ''
  configForm.modelVersion = row.modelVersion || 'V1.0'
  configForm.agentName = row.agentName || ''
  configForm.sceneCode = row.sceneCode || ''
  configForm.regionCodes = row.regionCodes?.length ? [...row.regionCodes] : ['ALL']
  regionScopeMode.value = configForm.regionCodes.length && !configForm.regionCodes.includes('ALL') ? 'CUSTOM' : 'ALL'
  configForm.featureRefs = (row.featureRefs || []).map((item: Record<string, any>, index: number) => ({
    id: item.featureId,
    featureId: item.featureId,
    featureCode: item.featureCode,
    featureName: item.featureName,
    featureColumn: item.featureColumn,
    timeGranularity: item.timeGranularity,
    requiredFlag: item.requiredFlag ?? 0,
    featureOrder: item.featureOrder ?? index + 1
  })).sort(compareFeatureByColumn)
  normalizeFeatureOrder()
  if (!configForm.regionCodes.length) {
    configForm.regionCodes = ['ALL']
  }
}

const loadScopeOptions = async () => {
  const regions = await listPage('/base-region', { page: 1, size: 1000 })
  regionOptions.value = regions.records.map((item: Record<string, any>) => ({
    label: item.regionName || item.regionCode,
    value: item.regionCode
  })).filter((item: SelectOption) => item.value)
  if (regionScopeMode.value === 'ALL') {
    configForm.regionCodes = regionOptions.value.map((item) => item.value)
  }
}

const loadFeatureOptions = async () => {
  featureLoading.value = true
  try {
    const features = await listPage('/model-feature-definition', { page: 1, size: 500 })
    featureOptions.value = features.records
      .filter((item: Record<string, any>) => item.enabled === 1)
      .map((item: Record<string, any>) => ({
        id: item.id,
        featureCode: item.featureCode,
        featureName: item.featureName,
        featureColumn: item.featureColumn,
        timeGranularity: item.timeGranularity
      }))
      .sort(compareFeatureByColumn)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '特征列表加载失败')
  } finally {
    featureLoading.value = false
  }
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: Record<string, any>) => {
  resetForm(row)
  dialogVisible.value = true
}

const openConfig = async (row: Record<string, any>) => {
  resetConfigForm(row)
  configDialogVisible.value = true
  if (!regionOptions.value.length) {
    await loadScopeOptions()
  }
  if (regionScopeMode.value === 'ALL') {
    configForm.regionCodes = regionOptions.value.map((item) => item.value)
  }
  if (!featureOptions.value.length) {
    await loadFeatureOptions()
  }
}

const saveData = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await updateRow(endpoint, { ...payload, id: editingId.value })
    } else {
      await createRow(endpoint, payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

const buildPayload = () => {
  return {
    configCode: form.configCode,
    configName: form.configName,
    modelVersion: form.modelVersion,
    agentCode: form.agentCode,
    sceneCode: form.sceneCode,
    description: form.description
  }
}

const handleRegionScopeModeChange = () => {
  configForm.regionCodes = regionScopeMode.value === 'ALL'
    ? regionOptions.value.map((item) => item.value)
    : []
}

const handleRegionCodesChange = () => {
  regionScopeMode.value = configForm.regionCodes.length === regionOptions.value.length ? 'ALL' : 'CUSTOM'
}

const isFeatureSelected = (row: FeatureOption) => {
  return configForm.featureRefs.some((item) => item.featureId === row.id)
}

const toggleFeatureSelection = (feature: FeatureOption) => {
  if (isFeatureSelected(feature)) {
    configForm.featureRefs = configForm.featureRefs.filter((item) => item.featureId !== feature.id)
    normalizeFeatureOrder()
    return
  }
  configForm.featureRefs.push({
    ...feature,
    featureId: feature.id,
    requiredFlag: 0,
    featureOrder: configForm.featureRefs.length + 1
  })
  normalizeFeatureOrder()
}

const featureRowClassName = ({ row }: { row: FeatureOption }) => {
  return isFeatureSelected(row) ? 'selected-feature-row' : ''
}

const clearSelectedFeatures = () => {
  configForm.featureRefs = []
}

const normalizeFeatureOrder = () => {
  configForm.featureRefs.sort(compareFeatureByColumn)
  configForm.featureRefs.forEach((item, index) => {
    item.featureOrder = index + 1
  })
}

const compareFeatureByColumn = (left: { featureColumn?: string }, right: { featureColumn?: string }) => {
  return featureColumnOrder(left.featureColumn) - featureColumnOrder(right.featureColumn)
}

const featureColumnOrder = (value?: string) => {
  const matched = value?.match(/^feature_(\d+)$/)
  return matched ? Number(matched[1]) : Number.MAX_SAFE_INTEGER
}

const timeGranularityText = (value: string) => {
  return ({ DAY: '日', TENDAY: '旬', MONTH: '月', YEAR: '年' } as Record<string, string>)[value] || value || '-'
}

const buildConfigPayload = () => {
  return {
    id: configuringId.value,
    regionCodes: configForm.regionCodes,
    industryCodes: [],
    customerCodes: [],
    featureRefs: configForm.featureRefs.map((item, index) => ({
      featureId: item.featureId,
      requiredFlag: item.requiredFlag,
      featureOrder: index + 1
    }))
  }
}

const saveConfig = async () => {
  if (!configuringId.value) return
  if (regionScopeMode.value === 'CUSTOM' && !configForm.regionCodes.length) {
    ElMessage.warning('请至少选择一个地区')
    return
  }
  configSaving.value = true
  try {
    await postJson(`${endpoint}/updateScope`, buildConfigPayload())
    ElMessage.success('配置保存成功')
    configDialogVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '配置保存失败')
  } finally {
    configSaving.value = false
  }
}

const removeRow = async (row: Record<string, any>) => {
  await ElMessageBox.confirm('删除模型后，对应作用范围配置也会同步删除，确认继续？', '删除确认', { type: 'warning' })
  try {
    await deleteRow(endpoint, row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(() => {
  void loadData()
})
</script>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: var(--app-gap);
}

.search-input {
  width: 300px;
}

.dialog-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
  row-gap: 2px;
}

.form-control {
  width: 100%;
}

.form-wide {
  grid-column: 1 / -1;
}

.config-overview {
  display: grid;
  grid-template-columns: 44px minmax(240px, 1fr) repeat(3, minmax(120px, 0.42fr));
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  padding: 14px;
  background: #F8FAFC;
  border: 1px solid #E6EAF0;
  border-radius: 8px;
}

.model-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: var(--app-primary);
  font-size: 22px;
  background: #EAF4FF;
  border-radius: 50%;
}

.model-title {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.model-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.model-title-row strong {
  overflow: hidden;
  color: #101828;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-title span {
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.summary-label {
  color: #667085;
  font-size: 12px;
}

.summary-item strong {
  overflow: hidden;
  color: #101828;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.config-section {
  border: 1px solid #E6EAF0;
  border-radius: 8px;
  padding: 14px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.section-title > .el-icon {
  margin-top: 2px;
  color: var(--app-primary);
  font-size: 20px;
}

.section-title div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.section-header h3 {
  margin: 0;
  color: #101828;
  font-size: 15px;
  font-weight: 700;
}

.section-title span {
  color: #667085;
  font-size: 12px;
  line-height: 1.4;
}

.feature-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.feature-picker-panel {
  margin-bottom: 12px;
  padding: 12px;
  background: #F8FAFC;
  border: 1px solid #D6E0EA;
  border-radius: 8px;
}

.feature-picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #344054;
  font-size: 14px;
  font-weight: 700;
}

.feature-picker-head > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected-count {
  font-weight: 600;
}

.feature-select-table {
  width: 100%;
}

.feature-select-table :deep(.el-table__row) {
  cursor: pointer;
}

.feature-select-table :deep(.selected-feature-row) {
  background: #F0F7FF;
}

.feature-select-table :deep(.selected-feature-row td.el-table__cell) {
  background: #F0F7FF;
}

.scope-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.scope-row {
  display: flex;
  align-items: center;
  min-height: 32px;
  margin-bottom: 12px;
}

.scope-region-field {
  grid-column: 1 / -1;
}

.scope-card {
  min-height: 260px;
  padding: 12px;
  background: #F8FAFC;
  border: 1px solid #E6EAF0;
  border-radius: 8px;
}

.scope-card.disabled {
  opacity: 0.65;
}

.scope-select-layout {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) 240px;
  gap: 12px;
}

.scope-select-main {
  min-width: 0;
}

.selected-region-panel {
  min-height: 234px;
  padding: 10px;
  background: #FFFFFF;
  border: 1px solid #E6EAF0;
  border-radius: 8px;
}

.selected-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
}

.selected-tags {
  display: flex;
  align-content: flex-start;
  gap: 8px;
  flex-wrap: wrap;
  min-height: 160px;
}

.empty-selection {
  color: #98A2B3;
  font-size: 13px;
}

.scope-search {
  margin-bottom: 8px;
}

.quick-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.region-group-list {
  max-height: 184px;
  overflow: auto;
}

.region-group + .region-group {
  margin-top: 8px;
}

.region-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 24px;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
}

.region-group :deep(.el-checkbox-group) {
  display: grid;
  grid-template-columns: 1fr;
  gap: 2px;
  padding-left: 12px;
}

.region-group :deep(.el-checkbox) {
  height: 22px;
  margin-right: 0;
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.dialog-form :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: #344054;
  font-weight: 600;
}

.column-header-with-tip {
  cursor: help;
  text-decoration: underline dotted;
  text-underline-offset: 3px;
}

@media (max-width: 760px) {
  .search-input {
    width: 100%;
  }

  .dialog-form {
    grid-template-columns: 1fr;
  }

  .config-overview,
  .scope-grid,
  .scope-select-layout {
    grid-template-columns: 1fr;
  }

  .model-avatar {
    display: none;
  }

  .feature-actions {
    width: 100%;
  }

  .region-group :deep(.el-checkbox-group) {
    grid-template-columns: 1fr;
  }
}
</style>
