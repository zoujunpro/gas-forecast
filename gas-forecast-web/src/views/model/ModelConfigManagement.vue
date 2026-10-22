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
        <PermissionButton type="primary" :icon="Refresh" :loading="syncing" permission="model:config:list" @click="syncModels(false)"
          >同步模型</PermissionButton
        >
      </template>

      <AppTable v-loading="loading" :data="records" stripe border>
        <el-table-column
          type="index"
          :index="rowIndex"
          label="序号"
          width="72"
          fixed
          class-name="id-column"
          label-class-name="id-column"
        />
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
            <el-tag
              v-if="field.prop === 'enabled'"
              class="enabled-toggle"
              :type="row.enabled === 1 ? 'success' : 'info'"
              effect="plain"
              :disable-transitions="true"
              @click.stop="toggleModelEnabled(row)"
            >{{ togglingId === row.id ? '处理中' : row.enabled === 1 ? '启用' : '停用' }}</el-tag>
            <ManagementTableCell v-else :field="field" :row="row" />
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="100"
          fixed="right"
          class-name="action-column"
          label-class-name="action-column"
        >
          <template #default="{ row }">
            <PermissionButton
              link
              type="success"
              :icon="Setting"
              permission="model:config:update"
              @click="openConfig(row)"
              >配置</PermissionButton
            >
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

    <AppDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="min(960px, calc(100vw - 32px))"
      align-center
      class="model-editor-dialog"
    >
      <template #header>
        <div class="model-editor-head">
          <div class="model-editor-icon">
            <el-icon><Box /></el-icon>
          </div>
          <div>
            <h2>{{ dialogTitle }}</h2>
            <p>{{ editingId ? '调整模型的基本资料和平台来源' : '从模型平台选择模型，并完善业务展示信息' }}</p>
          </div>
        </div>
      </template>

      <el-form ref="formRef" class="model-editor-form" :model="form" :rules="formRules" label-position="top">
        <section class="editor-section">
          <div class="editor-grid">
            <el-form-item label="所属智能体" prop="agentCode">
              <el-select
                v-model="form.agentCode"
                class="form-control"
                placeholder="请选择所属智能体"
                @change="handleAgentChange"
              >
                <el-option
                  v-for="item in modelAgentOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="模型编号" prop="configCode">
              <el-select
                v-model="form.configCode"
                class="form-control"
                filterable
                allow-create
                default-first-option
                :loading="platformModelsLoading"
                :empty-values="[]"
                placeholder="请选择或输入模型编号"
                @change="handlePlatformModelChange"
              >
                <el-option
                  v-for="item in filteredPlatformModels"
                  :key="item.model_code"
                  :label="`${item.model_name}（${item.model_code}）`"
                  :value="item.model_code"
                >
                  <div class="platform-model-option">
                    <span>{{ item.model_name }}</span>
                    <small>{{ item.model_code }} · {{ item.model_version }}</small>
                  </div>
                </el-option>
              </el-select>
              <div class="field-help">模型平台在线时可从列表选择；平台离线时可直接输入完整模型编号。</div>
            </el-form-item>
          </div>
        </section>

        <section class="editor-section">
          <div class="editor-grid">
            <el-form-item label="模型名称" prop="configName">
              <el-input v-model="form.configName" clearable maxlength="128" placeholder="请输入模型名称" />
            </el-form-item>
            <el-form-item label="模型版本" prop="modelVersion">
              <el-input v-model="form.modelVersion" clearable maxlength="32" placeholder="例如 V1.0" />
            </el-form-item>
            <el-form-item label="启用状态" prop="enabled">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="时间粒度" prop="timeGranularity">
              <el-select v-model="form.timeGranularity" class="form-control">
                <el-option v-for="item in granularityOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="支持的预测范围" required>
              <div class="number-range">
                <el-input-number v-model="form.minForecastHorizon" :min="1" :max="366" controls-position="right" />
                <span>至</span>
                <el-input-number v-model="form.maxForecastHorizon" :min="1" :max="366" controls-position="right" />
                <span>{{ granularityUnit(form.timeGranularity) }}</span>
              </div>
            </el-form-item>
            <el-form-item label="最短训练数据量" prop="minimumTrainingRows">
              <el-input-number v-model="form.minimumTrainingRows" class="number-control" :min="1" :max="1000000" controls-position="right" />
              <div class="field-help">按所选时间粒度计算的最少历史记录数。</div>
            </el-form-item>
            <el-form-item label="推荐训练周期数" prop="recommendedTrainingPeriods">
              <el-input-number v-model="form.recommendedTrainingPeriods" class="number-control" :min="1" :max="1000000" controls-position="right" />
              <div class="field-help">自动训练默认截取的最近周期数，单位跟随时间粒度。</div>
            </el-form-item>
            <el-form-item class="form-wide" label="默认训练范围">
              <el-radio-group v-model="form.defaultTrainingRangeMode">
                <el-radio value="RECOMMENDED">推荐</el-radio>
                <el-radio value="CUSTOM">自定义</el-radio>
                <el-radio value="ALL">全部历史数据</el-radio>
              </el-radio-group>
              <el-input-number v-if="form.defaultTrainingRangeMode === 'CUSTOM'" v-model="form.defaultTrainingPeriods" class="number-control range-period-input" :min="1" :max="1000000" controls-position="right" />
              <span v-if="form.defaultTrainingRangeMode === 'CUSTOM'" class="range-period-unit">{{ granularityUnit(form.timeGranularity) }}</span>
              <div class="field-help">推荐使用模型平台建议周期；自定义使用填写的周期；全部使用当前范围内所有历史数据。</div>
            </el-form-item>
            <el-form-item class="form-wide" label="训练数据要求说明">
              <el-input v-model="form.trainingDataRequirement" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="例如：至少140天连续日数据，销量不能为空；建议使用最近2年完整历史。" />
            </el-form-item>
            <el-form-item class="form-wide" label="模型描述" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="4"
                maxlength="500"
                show-word-limit
                placeholder="简要说明模型的适用场景、预测目标或数据要求"
              />
            </el-form-item>
          </div>
        </section>
      </el-form>
      <template #footer>
        <el-button type="info" plain @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveData">保存</el-button>
      </template>
    </AppDialog>

    <AppDialog
      v-model="configDialogVisible"
      eyebrow="模型配置"
      :title="configDialogTitle"
      width="min(1080px, calc(100vw - 32px))"
      align-center
    >
      <div class="config-overview">
        <div class="model-avatar">
          <el-icon><Setting /></el-icon>
        </div>
        <div class="model-title">
          <div class="model-title-row">
            <strong>{{ configForm.configName || '模型配置' }}</strong>
            <el-tag :type="configForm.enabled === 1 ? 'success' : 'info'" effect="light">{{ configForm.enabled === 1 ? '启用' : '停用' }}</el-tag>
            <el-switch v-model="configForm.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
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
              :disabled="regionScopeMode === 'ALL'"
              placeholder="请选择地区"
              @change="handleScopeCodesChange"
            >
              <el-option v-for="item in regionOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <div class="scope-row">
            <el-radio-group v-model="industryScopeMode" @change="handleIndustryScopeModeChange">
              <el-radio value="CUSTOM">指定行业</el-radio><el-radio value="ALL">所有行业</el-radio>
            </el-radio-group>
          </div>
          <el-form-item label="行业">
            <el-select v-model="configForm.industryCodes" class="form-control" multiple filterable :disabled="industryScopeMode === 'ALL'" placeholder="请选择行业" @change="handleScopeCodesChange">
              <el-option v-for="item in industryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <div class="scope-row">
            <el-radio-group v-model="customerScopeMode" @change="handleCustomerScopeModeChange">
              <el-radio value="CUSTOM">指定客户</el-radio><el-radio value="ALL">所有客户</el-radio>
            </el-radio-group>
          </div>
          <el-form-item label="客户">
            <el-select v-model="configForm.customerCodes" class="form-control" multiple filterable collapse-tags collapse-tags-tooltip :disabled="customerScopeMode === 'ALL'" placeholder="请选择客户">
              <el-option v-for="item in filteredCustomerOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <div class="field-help">客户会根据已选地区和行业自动过滤。</div>
          </el-form-item>
        </section>

        <section class="config-section">
          <header class="section-header"><div class="section-title"><el-icon><TrendCharts /></el-icon><div><h3>模型能力边界</h3><span>由模型平台定义并自动同步，仅供查看，不需要人工配置</span></div></div><el-tag type="info" effect="plain">平台同步</el-tag></header>
          <div class="capability-readonly-grid">
            <div class="capability-item"><span>时间粒度</span><strong>{{ timeGranularityText(configForm.timeGranularity) }}</strong></div>
            <div class="capability-item"><span>最短训练数据量</span><strong>{{ configForm.minimumTrainingRows || '-' }} {{ granularityUnit(configForm.timeGranularity) }}</strong></div>
            <div class="capability-item"><span>推荐训练周期</span><strong>{{ configForm.recommendedTrainingPeriods || '-' }} {{ granularityUnit(configForm.timeGranularity) }}</strong></div>
            <div class="capability-item"><span>支持的预测范围</span><strong>{{ configForm.minForecastHorizon || '-' }} 至 {{ configForm.maxForecastHorizon || '-' }} {{ granularityUnit(configForm.timeGranularity) }}</strong></div>
            <div class="capability-item capability-item-wide"><span>训练数据要求</span><p>{{ configForm.trainingDataRequirement || '模型平台暂未提供说明' }}</p></div>
          </div>
          <el-form-item class="training-range-setting" label="默认训练范围">
            <el-radio-group v-model="configForm.defaultTrainingRangeMode">
              <el-radio value="RECOMMENDED">推荐（{{ configForm.recommendedTrainingPeriods || '-' }} {{ granularityUnit(configForm.timeGranularity) }}）</el-radio>
              <el-radio value="CUSTOM">自定义</el-radio>
              <el-radio value="ALL">全部历史数据</el-radio>
            </el-radio-group>
            <el-input-number v-if="configForm.defaultTrainingRangeMode === 'CUSTOM'" v-model="configForm.defaultTrainingPeriods" :min="1" :max="1000000" controls-position="right" />
            <span v-if="configForm.defaultTrainingRangeMode === 'CUSTOM'">{{ granularityUnit(configForm.timeGranularity) }}</span>
          </el-form-item>
        </section>

        <section class="config-section">
          <header class="section-header">
            <div class="section-title">
              <el-icon><Histogram /></el-icon>
              <div>
                <h3>输入特征</h3>
                <span>特征统一基于日级原始数据，训练时按模型时间粒度自动聚合</span>
              </div>
            </div>
            <div class="feature-actions">
              <el-tag class="selected-count" effect="plain">已选 {{ selectedFeatureCount }}</el-tag>
              <el-button plain :disabled="!configForm.featureRefs.length" @click="clearSelectedFeatures"
                >清空选择</el-button
              >
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
              empty-text="暂无可用特征"
              :row-class-name="featureRowClassName"
              @row-click="toggleFeatureSelection"
            >
              <el-table-column label="" width="48" align="center">
                <template #default="{ row }">
                  <el-checkbox
                    :model-value="isFeatureSelected(row)"
                    @click.stop
                    @change="toggleFeatureSelection(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column type="index" label="序号" width="64" />
              <el-table-column prop="featureName" label="特征名称" min-width="160" show-overflow-tooltip />
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
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Box, Histogram, Location, Refresh, Search, Setting, TrendCharts } from '@element-plus/icons-vue'
import { createRow, getJson, listPage, postJson, updateRow } from '@/api/management'
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
const syncing = ref(false)
const togglingId = ref<number | null>(null)
const dialogVisible = ref(false)
const configDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const configuringId = ref<number | null>(null)
const form = reactive({
  configCode: '',
  configName: '',
  modelVersion: 'V1.0',
  enabled: 1,
  agentCode: 'winter-supply',
  sceneCode: 'WINTER_SUPPLY',
  timeGranularity: 'TENDAY',
  minForecastHorizon: 1,
  maxForecastHorizon: 18,
  minimumTrainingRows: 18,
  recommendedTrainingPeriods: 18,
  defaultTrainingRangeMode: 'RECOMMENDED',
  defaultTrainingPeriods: 730,
  trainingDataRequirement: '',
  description: ''
})
const configForm = reactive({
  configCode: '',
  configName: '',
  modelVersion: '',
  enabled: 1,
  agentName: '',
  sceneCode: '',
  regionCodes: [] as string[],
  industryCodes: [] as string[],
  customerCodes: [] as string[],
  timeGranularity: 'DAY',
  minForecastHorizon: 1,
  maxForecastHorizon: 30,
  minimumTrainingRows: 140,
  recommendedTrainingPeriods: 140,
  defaultTrainingRangeMode: 'RECOMMENDED',
  defaultTrainingPeriods: 730,
  trainingDataRequirement: '',
  featureRefs: [] as FeatureRefRow[]
})

interface SelectOption {
  label: string
  value: string
}

interface PlatformModel {
  agent_code: string
  model_code: string
  model_version: string
  model_name: string
  description: string
  capabilities: string[]
  training_data_range?: {
    frequency?: string
    minimum?: number
    recommended?: number
    description?: string
  }
}

interface FeatureOption {
  id: number
  featureCode: string
  featureName: string
}

interface FeatureRefRow extends FeatureOption {
  featureId: number
  outputFeatureCode?: string
  requiredFlag: number
  featureOrder: number
}

const regionOptions = ref<SelectOption[]>([])
const industryOptions = ref<SelectOption[]>([])
const customerOptions = ref<(SelectOption & { regionCode?: string; industryCode?: string })[]>([])
const featureOptions = ref<FeatureOption[]>([])
const featureLoading = ref(false)
const platformModels = ref<PlatformModel[]>([])
const platformModelsLoading = ref(false)
const regionScopeMode = ref<'ALL' | 'CUSTOM'>('ALL')
const industryScopeMode = ref<'ALL' | 'CUSTOM'>('ALL')
const customerScopeMode = ref<'ALL' | 'CUSTOM'>('ALL')
const granularityOptions = [
  { label: '日', value: 'DAY' }, { label: '旬', value: 'TENDAY' }, { label: '月', value: 'MONTH' }
]

const formRules: FormRules = {
  configCode: [{ required: true, message: '请选择模型', trigger: 'change' }],
  configName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  agentCode: [{ required: true, message: '请选择所属智能体', trigger: 'change' }],
  sceneCode: [{ required: true, message: '请选择场景', trigger: 'change' }]
}

const { loading, keyword, page, size, total, records, loadData, searchData, resetSearch, handleSizeChange, rowIndex } =
  usePageQuery<Record<string, any>>({
    errorMessage: '模型列表加载失败',
    fetcher: ({ page, size, keyword, signal }) =>
      listPage(endpoint, { page, size, keyword: keyword || undefined }, signal)
  })

const dialogTitle = computed(() => (editingId.value ? '编辑模型' : '新增模型'))
const configDialogTitle = computed(() => `${configForm.configName || '模型'}配置`)
const selectedAgent = computed(() => modelAgentOptions.find((item) => item.value === form.agentCode))
const selectedFeatureCount = computed(() => configForm.featureRefs.length)
const filteredPlatformModels = computed(() => platformModels.value.filter((item) => item.agent_code === form.agentCode))
const filteredCustomerOptions = computed(() => customerOptions.value.filter((item) =>
  (!configForm.regionCodes.length || !item.regionCode || configForm.regionCodes.includes(item.regionCode)) &&
  (!configForm.industryCodes.length || !item.industryCode || configForm.industryCodes.includes(item.industryCode))))
const granularityUnit = (value: string) => ({ DAY: '天', TENDAY: '旬', MONTH: '月' } as Record<string, string>)[value] || '期'
const capabilityDefaults = (agentCode: string) => agentCode === 'monthly-sales'
  ? { timeGranularity: 'MONTH', maxForecastHorizon: 24, minimumTrainingRows: 24, recommendedTrainingPeriods: 36 }
  : agentCode === 'winter-supply'
    ? { timeGranularity: 'TENDAY', maxForecastHorizon: 18, minimumTrainingRows: 18, recommendedTrainingPeriods: 108 }
    : { timeGranularity: 'DAY', maxForecastHorizon: 90, minimumTrainingRows: 140, recommendedTrainingPeriods: 730 }
const syncSceneCode = () => {
  form.sceneCode = selectedAgent.value?.sceneCode || ''
}

const handleAgentChange = () => {
  syncSceneCode()
  Object.assign(form, capabilityDefaults(form.agentCode))
  if (!filteredPlatformModels.value.some((item) => item.model_code === form.configCode)) {
    form.configCode = ''
  }
}

const _loadPlatformModels = async () => {
  platformModelsLoading.value = true
  try {
    const response = await getJson<PlatformModel[]>('/model-platform/models')
    platformModels.value = Array.isArray(response.data) ? response.data : []
  } catch (error) {
    platformModels.value = []
    ElMessage.error(error instanceof Error ? error.message : '模型平台列表加载失败')
  } finally {
    platformModelsLoading.value = false
  }
}

const handlePlatformModelChange = (modelCode: string) => {
  const model = platformModels.value.find((item) => item.model_code === modelCode)
  if (!model) return
  form.configName = model.model_name
  form.modelVersion = model.model_version
  form.agentCode = model.agent_code
  form.description = model.description
  const range = model.training_data_range
  if (range?.frequency) form.timeGranularity = String(range.frequency).toUpperCase()
  if (range?.minimum) {
    form.minimumTrainingRows = range.minimum
    form.recommendedTrainingPeriods = range.minimum
  }
  if (range?.description) form.trainingDataRequirement = range.description
  syncSceneCode()
}

const _resetForm = (row?: Record<string, any>) => {
  editingId.value = row?.id ?? null
  form.configCode = row?.configCode || ''
  form.configName = row?.configName || ''
  form.modelVersion = row?.modelVersion || 'V1.0'
  form.enabled = row?.enabled ?? 1
  form.agentCode = row?.agentCode || 'winter-supply'
  form.sceneCode = row?.sceneCode || selectedAgent.value?.sceneCode || 'WINTER_SUPPLY'
  const defaults = capabilityDefaults(form.agentCode)
  form.timeGranularity = row?.timeGranularity || defaults.timeGranularity
  form.minForecastHorizon = row?.minForecastHorizon || 1
  form.maxForecastHorizon = row?.maxForecastHorizon || defaults.maxForecastHorizon
  form.minimumTrainingRows = row?.minimumTrainingRows || defaults.minimumTrainingRows
  form.recommendedTrainingPeriods = row?.recommendedTrainingPeriods || defaults.recommendedTrainingPeriods
  form.defaultTrainingRangeMode = row?.defaultTrainingRangeMode || 'RECOMMENDED'
  form.defaultTrainingPeriods = row?.defaultTrainingPeriods || form.recommendedTrainingPeriods
  form.trainingDataRequirement = row?.trainingDataRequirement || ''
  form.description = row?.description || ''
  syncSceneCode()
}

const resetConfigForm = (row: Record<string, any>) => {
  configuringId.value = row.id
  configForm.configCode = row.configCode || ''
  configForm.configName = row.configName || ''
  configForm.modelVersion = row.modelVersion || 'V1.0'
  configForm.enabled = row.enabled ?? 1
  configForm.agentName = row.agentName || ''
  configForm.sceneCode = row.sceneCode || ''
  configForm.regionCodes = row.regionCodes?.length ? [...row.regionCodes] : []
  configForm.industryCodes = row.industryCodes?.length ? [...row.industryCodes] : []
  configForm.customerCodes = row.customerCodes?.length ? [...row.customerCodes] : []
  regionScopeMode.value = configForm.regionCodes.length ? 'CUSTOM' : 'ALL'
  industryScopeMode.value = configForm.industryCodes.length ? 'CUSTOM' : 'ALL'
  customerScopeMode.value = configForm.customerCodes.length ? 'CUSTOM' : 'ALL'
  configForm.timeGranularity = row.timeGranularity || 'DAY'
  configForm.minForecastHorizon = row.minForecastHorizon || 1
  configForm.maxForecastHorizon = row.maxForecastHorizon || 30
  configForm.minimumTrainingRows = row.minimumTrainingRows || 140
  configForm.recommendedTrainingPeriods = row.recommendedTrainingPeriods || configForm.minimumTrainingRows
  configForm.defaultTrainingRangeMode = row.defaultTrainingRangeMode || 'RECOMMENDED'
  configForm.defaultTrainingPeriods = row.defaultTrainingPeriods || configForm.recommendedTrainingPeriods
  configForm.trainingDataRequirement = row.trainingDataRequirement || ''
  configForm.featureRefs = (row.featureRefs || [])
    .map((item: Record<string, any>, index: number) => ({
      id: item.featureId,
      featureId: item.featureId,
      featureCode: item.featureCode,
      featureName: item.featureName,
      outputFeatureCode: item.outputFeatureCode || item.featureCode,
      requiredFlag: item.requiredFlag ?? 0,
      featureOrder: item.featureOrder ?? index + 1
    }))
    .sort(compareFeature)
  normalizeFeatureOrder()
}

const loadScopeOptions = async () => {
  const [regions, industries, customers] = await Promise.all([
    listPage('/base-region', { page: 1, size: 1000 }),
    listPage('/base-industry', { page: 1, size: 1000 }),
    listPage('/base-customer', { page: 1, size: 1000 })
  ])
  regionOptions.value = regions.records
    .map((item: Record<string, any>) => ({
      label: item.regionName || item.regionCode,
      value: item.regionCode
    }))
    .filter((item: SelectOption) => item.value)
  industryOptions.value = industries.records.map((item: Record<string, any>) => ({ label: item.industryName || item.industryCode, value: item.industryCode })).filter((item: SelectOption) => item.value)
  customerOptions.value = customers.records.map((item: Record<string, any>) => ({ label: `${item.customerName || item.customerCode}${item.regionName ? `（${item.regionName}）` : ''}`, value: item.customerCode, regionCode: item.regionCode, industryCode: item.industryCode })).filter((item: SelectOption) => item.value)
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
        featureName: item.featureName
      }))
      .sort(compareFeature)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '特征列表加载失败')
  } finally {
    featureLoading.value = false
  }
}

const syncModels = async (silent: boolean) => {
  syncing.value = true
  try {
    const response = await postJson<{ data?: number }>(`${endpoint}/sync`, {})
    if (!silent) ElMessage.success(`已同步 ${response.data ?? 0} 个模型`)
  } catch (error) {
    if (!silent) ElMessage.error(error instanceof Error ? error.message : '模型同步失败')
  } finally {
    syncing.value = false
    await loadData()
  }
}

const openConfig = async (row: Record<string, any>) => {
  resetConfigForm(row)
  configDialogVisible.value = true
  if (!regionOptions.value.length || !industryOptions.value.length || !customerOptions.value.length) {
    await loadScopeOptions()
  }
  if (!featureOptions.value.length) {
    await loadFeatureOptions()
  }
}

const saveData = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  if (form.minForecastHorizon > form.maxForecastHorizon) {
    ElMessage.warning('最小预测周期不能大于最大预测周期')
    return
  }
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
    enabled: form.enabled,
    agentCode: form.agentCode,
    sceneCode: form.sceneCode,
    timeGranularity: form.timeGranularity,
    minForecastHorizon: form.minForecastHorizon,
    maxForecastHorizon: form.maxForecastHorizon,
    minimumTrainingRows: form.minimumTrainingRows,
    recommendedTrainingPeriods: form.recommendedTrainingPeriods,
    defaultTrainingRangeMode: form.defaultTrainingRangeMode,
    defaultTrainingPeriods: form.defaultTrainingPeriods,
    trainingDataRequirement: form.trainingDataRequirement,
    description: form.description
  }
}

const handleRegionScopeModeChange = () => {
  configForm.regionCodes = []
  handleScopeCodesChange()
}

const handleScopeCodesChange = () => {
  configForm.customerCodes = configForm.customerCodes.filter((code) => filteredCustomerOptions.value.some((item) => item.value === code))
}

const handleIndustryScopeModeChange = () => { configForm.industryCodes = []; handleScopeCodesChange() }
const handleCustomerScopeModeChange = () => { configForm.customerCodes = [] }

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
    outputFeatureCode: feature.featureCode,
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
  configForm.featureRefs.sort(compareFeature)
  configForm.featureRefs.forEach((item, index) => {
    item.featureOrder = index + 1
  })
}

const compareFeature = (left: { featureCode?: string }, right: { featureCode?: string }) =>
  (left.featureCode || '').localeCompare(right.featureCode || '', 'zh-CN')

const timeGranularityText = (value: string) => {
  return ({ DAY: '日', TENDAY: '旬', MONTH: '月', YEAR: '年' } as Record<string, string>)[value] || value || '-'
}

const buildConfigPayload = () => {
  return {
    id: configuringId.value,
    enabled: configForm.enabled,
    regionCodes: configForm.regionCodes,
    industryCodes: configForm.industryCodes,
    customerCodes: configForm.customerCodes,
    defaultTrainingRangeMode: configForm.defaultTrainingRangeMode,
    defaultTrainingPeriods: configForm.defaultTrainingPeriods,
    featureRefs: configForm.featureRefs.map((item, index) => ({
      featureId: item.featureId,
      outputFeatureCode: item.outputFeatureCode || item.featureCode,
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
  if (industryScopeMode.value === 'CUSTOM' && !configForm.industryCodes.length) { ElMessage.warning('请至少选择一个行业'); return }
  if (customerScopeMode.value === 'CUSTOM' && !configForm.customerCodes.length) { ElMessage.warning('请至少选择一个客户'); return }
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

const toggleModelEnabled = async (row: Record<string, any>) => {
  if (togglingId.value !== null) return
  const enabled = row.enabled === 1 ? 0 : 1
  togglingId.value = row.id
  try {
    await postJson(`${endpoint}/updateEnabled`, { id: row.id, enabled })
    row.enabled = enabled
    ElMessage.success(enabled === 1 ? '模型已启用' : '模型已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型状态更新失败')
  } finally {
    togglingId.value = null
  }
}

onMounted(() => {
  void syncModels(true)
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

.model-editor-head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.model-editor-head h2,
.model-editor-head p {
  margin: 0;
}

.model-editor-head h2 {
  color: #172033;
  font-size: 20px;
}

.model-editor-head p {
  margin-top: 5px;
  color: #8490a5;
  font-size: 13px;
}

.model-editor-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--app-primary);
  background: #edf5ff;
  border: 1px solid #dcecff;
  border-radius: 12px;
}

.model-editor-icon {
  width: 46px;
  height: 46px;
  font-size: 23px;
}

.model-editor-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.editor-section {
  padding: 18px 20px 4px;
  background: #fbfcfe;
  border: 1px solid #e6ebf2;
  border-radius: 12px;
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
}

.form-control {
  width: 100%;
}

.number-control { width: 100%; }
.number-range { width: 100%; display: flex; align-items: center; gap: 8px; }
.number-range :deep(.el-input-number) { width: 132px; }
.number-range > span { flex: none; color: #667085; }
.capability-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 0 18px; }
.capability-readonly-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.capability-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 78px;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.capability-item span { color: #667085; font-size: 13px; }
.capability-item strong { color: #101828; font-size: 17px; font-weight: 650; }
.capability-item p { margin: 0; color: #344054; font-size: 14px; line-height: 1.65; }
.capability-item-wide { grid-column: 1 / -1; min-height: auto; }

.platform-model-option {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}
.platform-model-option small {
  color: #98a2b3;
  font-size: 13px;
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
  background: #f8fafc;
  border: 1px solid #e6eaf0;
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
  background: #eaf4ff;
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
  font-size: 13px;
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
  font-size: 13px;
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
  border: 1px solid #e6eaf0;
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
  font-weight: 600;
}

.section-title span {
  color: #667085;
  font-size: 13px;
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
  background: #f8fafc;
  border: 1px solid #d6e0ea;
  border-radius: 8px;
}

.feature-picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #344054;
  font-size: 14px;
  font-weight: 600;
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
  background: #f0f7ff;
}

.feature-select-table :deep(.selected-feature-row td.el-table__cell) {
  background: #f0f7ff;
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
  background: #f8fafc;
  border: 1px solid #e6eaf0;
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
  background: #ffffff;
  border: 1px solid #e6eaf0;
  border-radius: 8px;
}

.selected-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

.selected-tags {
  display: flex;
  align-content: flex-start;
  gap: 8px;
  flex-wrap: wrap;
  min-height: 160px;
}

.empty-selection {
  color: #98a2b3;
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
  font-weight: 600;
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

.model-editor-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.model-editor-form :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: #344054;
  font-weight: 600;
}

:global(.model-editor-dialog .el-dialog__body) {
  padding: 18px 22px 8px;
  background: #f7f9fc;
}

:global(.model-editor-dialog .el-dialog__footer) {
  padding-top: 16px;
  background: #ffffff;
}

.column-header-with-tip {
  cursor: help;
  text-decoration: underline dotted;
  text-underline-offset: 3px;
}

.enabled-toggle {
  cursor: pointer;
  user-select: none;
}

@media (max-width: 760px) {
  .search-input {
    width: 100%;
  }

  .editor-grid {
    grid-template-columns: 1fr;
  }

  .capability-grid,
  .capability-readonly-grid { grid-template-columns: 1fr; }

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
