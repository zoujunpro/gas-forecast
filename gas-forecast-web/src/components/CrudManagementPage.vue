<template>
  <section class="crud-page">
    <PageBreadcrumb />

    <AppTablePanel>
      <template #filters>
        <el-input
          v-model="keyword"
          class="search-input"
          clearable
          :prefix-icon="Search"
          :placeholder="config.searchPlaceholder"
          @clear="searchData"
          @keyup.enter="searchData"
        />
        <template v-for="field in config.filterFields || []" :key="field.prop">
          <el-date-picker
            v-if="field.type === 'dateRange'"
            v-model="filters[field.prop]"
            class="filter-control"
            type="daterange"
            unlink-panels
            clearable
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :style="{ width: `${field.width || 260}px` }"
            @change="searchData"
          />
          <el-select
            v-else-if="field.type === 'select'"
            v-model="filters[field.prop]"
            class="filter-control"
            clearable
            :placeholder="field.placeholder || field.label"
            :style="{ width: `${field.width || 160}px` }"
            @change="searchData"
            @clear="searchData"
          >
            <el-option v-for="option in field.options || []" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-input
            v-else
            v-model="filters[field.prop]"
            class="filter-control"
            clearable
            :placeholder="field.placeholder || field.label"
            :style="{ width: `${field.width || 180}px` }"
            @clear="searchData"
            @keyup.enter="searchData"
          />
        </template>
        <el-button type="primary" :icon="Search" @click="searchData">查询</el-button>
        <el-button type="info" plain @click="resetAllSearch">重置</el-button>
      </template>

      <template #actions>
        <slot name="table-actions" :reload="loadData" />
        <PermissionButton v-if="!config.readonly" type="primary" :icon="Plus" :permission="config.permissions?.create" @click="openCreate">新增</PermissionButton>
      </template>

      <AppTable v-loading="loading" :data="records" stripe border @sort-change="handleSortChange">
        <el-table-column type="index" :index="rowIndex" label="序号" width="72" fixed class-name="id-column" label-class-name="id-column" />
        <el-table-column
          v-for="field in config.tableFields"
          :key="field.prop"
          :prop="field.prop"
          :label="field.label"
          :min-width="field.minWidth || 120"
          :width="field.width"
          :align="field.align"
          :sortable="field.sortable"
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
        <el-table-column v-if="!config.readonly" label="操作" :width="config.trainExecution ? 210 : 150" fixed="right" class-name="action-column" label-class-name="action-column">
          <template #default="{ row }">
            <el-button v-if="config.trainExecution" link type="success" @click="openExecute(row)">执行</el-button>
            <PermissionButton link type="primary" :permission="config.permissions?.update" @click="openEdit(row)">编辑</PermissionButton>
            <PermissionButton link type="danger" :permission="config.permissions?.delete" @click="removeRow(row)">删除</PermissionButton>
          </template>
        </el-table-column>
        <el-table-column v-else-if="config.featureDetailProp" label="操作" width="110" fixed="right" class-name="action-column" label-class-name="action-column">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openFeatureDetail(row)">查看特征</el-button>
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

    <AppDialog v-model="dialogVisible" eyebrow="后台数据管理" :title="dialogTitle" width="680px" align-center>
      <el-form ref="formRef" class="dialog-form" :model="form" :rules="formRules" label-position="top">
        <el-form-item v-for="field in visibleFormFields" :key="field.prop" :label="field.label" :prop="field.prop">
          <FormFieldRenderer :field="field" :model="form" :option-map="formOptionMap" @option-select="handleFormOptionSelect" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="info" plain @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="executionMode ? continueExecution() : saveData()">{{ executionMode ? '继续执行' : '保存' }}</el-button>
      </template>
    </AppDialog>

    <AppDialog v-model="previewVisible" eyebrow="模型训练执行" title="训练数据预览" width="1120px" align-center>
      <div class="preview-summary">
        <span>训练编码：{{ executionConfig?.trainCode || '-' }}</span>
        <span>时间格式：{{ granularityText(executionConfig?.timeGranularity) }}</span>
        <span>训练方式：{{ executionConfig?.trainMode === 'RANGE' ? '指定时间范围' : '最近时间' }}</span>
        <span>数据量：{{ previewTotal }}</span>
      </div>
      <div class="preview-scope">
        <span>训练区域：{{ executionConfig?.regionName || '全部' }}</span>
        <span>训练行业：{{ executionConfig?.industryName || '全部' }}</span>
        <span>训练客户：{{ executionConfig?.customerName || '全部' }}</span>
        <span>时间范围：{{ previewTimeRange }}</span>
      </div>
      <el-table v-loading="previewLoading" :data="previewRows" border stripe height="430px">
        <el-table-column prop="statDate" label="统计日期" min-width="110" fixed />
        <el-table-column prop="timeGranularity" label="时间格式" min-width="90">
          <template #default="{ row }">{{ granularityText(row.timeGranularity) }}</template>
        </el-table-column>
        <el-table-column prop="regionName" label="区域" min-width="110" />
        <el-table-column prop="industryName" label="行业" min-width="110" />
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column prop="gasSales" label="天然气销量" min-width="120" align="right" />
        <el-table-column prop="featureDetails" label="特征数量" min-width="90" align="right">
          <template #default="{ row }">{{ Array.isArray(row.featureDetails) ? row.featureDetails.length : 0 }}</template>
        </el-table-column>
      </el-table>
      <div class="preview-pagination">
        <AppPagination
          v-model:current-page="previewPage"
          v-model:page-size="previewSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="previewTotal"
          @current-change="loadPreviewData"
          @size-change="handlePreviewSizeChange"
        />
      </div>
      <template #footer>
        <el-button type="info" plain @click="previewVisible = false">取消</el-button>
        <el-button type="primary" :loading="executing" :disabled="previewRows.length === 0" @click="executeTraining">确认执行</el-button>
      </template>
    </AppDialog>

    <el-drawer v-model="featureDetailVisible" title="特征详情" size="520px">
      <div v-if="selectedRow" class="feature-detail">
        <div class="feature-summary">
          <span>{{ selectedRow.statDate || '-' }}</span>
          <span>{{ selectedRow.regionName || '-' }}</span>
          <span>{{ selectedRow.industryName || '-' }}</span>
        </div>
        <el-table v-if="featureDetailEntries.length" :data="featureDetailEntries" border stripe height="calc(100vh - 190px)">
          <el-table-column prop="featureNo" label="特征编号" min-width="120" />
          <el-table-column prop="featureCode" label="特征编码" min-width="160" show-overflow-tooltip />
          <el-table-column prop="featureValue" label="特征值" min-width="120" align="right" />
        </el-table>
        <el-empty v-if="!featureDetailEntries.length" description="暂无特征值" />
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, View } from '@element-plus/icons-vue'
import { createRow, deleteRow, listPage, postJson, updateRow, type PageRequest } from '@/api/management'
import { usePageQuery } from '@/composables/usePageQuery'
import AppDialog from '@/components/AppDialog.vue'
import AppPagination from '@/components/AppPagination.vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'
import ManagementTableCell from '@/components/ManagementTableCell.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import type { BaseDataFieldConfig, BaseDataPageConfig, Option } from '@/views/shared/managementTypes'

const props = defineProps<{
  pageConfig: BaseDataPageConfig
}>()

const saving = ref(false)
const executing = ref(false)
const dialogVisible = ref(false)
const previewVisible = ref(false)
const previewLoading = ref(false)
const executionMode = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<Record<string, any>>({})
const filters = reactive<Record<string, any>>({})
const sortField = ref('')
const sortOrder = ref('')
const featureDetailVisible = ref(false)
const selectedRow = ref<Record<string, any> | null>(null)
const executionConfig = ref<Record<string, any> | null>(null)
const previewRows = ref<Record<string, any>[]>([])
const previewTotal = ref(0)
const previewPage = ref(1)
const previewSize = ref(20)
const formOptionMap = reactive<Record<string, Option[]>>({})

const config = computed(() => props.pageConfig)
const visibleFormFields = computed(() => config.value.formFields.filter((field) => {
  if (!field.visibleWhen) {
    return true
  }
  return form[field.visibleWhen.prop] === field.visibleWhen.value
}))
const dialogTitle = computed(() => `${executionMode.value ? '执行' : editingId.value ? '编辑' : '新增'}${config.value.title.replace('管理', '')}`)
const previewTimeRange = computed(() => {
  if (!executionConfig.value) {
    return '-'
  }
  if (executionConfig.value.trainMode === 'RECENT') {
    return `最近 ${executionConfig.value.recentPeriods || 36} 个周期`
  }
  return `${executionConfig.value.trainStartDate || '-'} 至 ${executionConfig.value.trainEndDate || '-'}`
})
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  visibleFormFields.value.forEach((field) => {
    const fieldRules = []
    if (field.required) {
      fieldRules.push({ required: true, message: `请输入${field.label}`, trigger: field.type === 'select' || field.type === 'radio' ? 'change' : 'blur' })
    }
    if (field.maxLength) {
      fieldRules.push({ max: field.maxLength, message: `${field.label}不能超过${field.maxLength}个字符`, trigger: 'blur' })
    }
    if (field.pattern) {
      fieldRules.push({ pattern: new RegExp(field.pattern), message: `${field.label}格式不正确`, trigger: 'blur' })
    }
    if (fieldRules.length) {
      rules[field.prop] = fieldRules
    }
  })
  return rules
})

const {
  loading,
  keyword,
  page,
  size,
  total,
  records,
  loadData,
  searchData,
  resetSearch: resetKeywordSearch,
  handleSizeChange,
  rowIndex
} = usePageQuery<Record<string, any>>({
  errorMessage: '列表加载失败',
  fetcher: ({ page, size, keyword }) => listPage(config.value.endpoint, {
    page,
    size,
    keyword: keyword || undefined,
    sortField: sortField.value || undefined,
    sortOrder: sortOrder.value || undefined,
    ...filterPayload.value
  })
})

const filterPayload = computed(() => {
  const payload = Object.fromEntries(
    Object.entries(filters)
      .map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value])
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
  config.value.filterFields?.forEach((field) => {
    if (field.type === 'dateRange') {
      const value = filters[field.prop]
      delete payload[field.prop]
      if (Array.isArray(value) && value.length === 2) {
        payload[field.startProp || `${field.prop}Start`] = value[0]
        payload[field.endProp || `${field.prop}End`] = value[1]
      }
    }
  })
  return payload
})

const featureDetailEntries = computed(() => {
  const detailProp = config.value.featureDetailProp
  if (!selectedRow.value || !detailProp) {
    return []
  }
  const details = selectedRow.value.featureDetails
  if (Array.isArray(details)) {
    return details
  }
  const values = selectedRow.value[detailProp] || {}
  return Object.entries(values).map(([key, value]) => ({ featureNo: key, featureCode: '-', featureValue: value }))
})

const resetForm = (row?: Record<string, any>) => {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, row ? { ...row } : { ...config.value.emptyForm })
}

const formatSourceOption = (row: Record<string, any>, field: BaseDataFieldConfig): Option => {
  const source = field.optionSource
  if (!source) {
    return { label: '', value: '' }
  }
  const value = row[source.valueProp]
  const label = source.labelTemplate === 'nameWithCode'
    ? `${row[source.labelProp] || '-'} (${value || '-'})`
    : row[source.labelProp]
  return { label, value, raw: row }
}

const loadFormOptions = async () => {
  const sourceFields = config.value.formFields.filter((field) => field.optionSource)
  await Promise.all(sourceFields.map(async (field) => {
    const source = field.optionSource
    if (!source) {
      return
    }
    try {
      const data = await listPage(source.endpoint, { page: 1, size: source.size || 1000 })
      formOptionMap[field.prop] = data.records.map((row) => formatSourceOption(row, field))
    } catch (error) {
      ElMessage.error(`${field.label}选项加载失败`)
      formOptionMap[field.prop] = []
    }
  }))
}

const handleFormOptionSelect = (field: BaseDataFieldConfig, option?: Option) => {
  if (!field.fillProps) {
    return
  }
  Object.entries(field.fillProps).forEach(([targetProp, sourceProp]) => {
    form[targetProp] = option?.raw?.[sourceProp] || ''
  })
}

const resetFilters = () => {
  Object.keys(filters).forEach((key) => delete filters[key])
  config.value.filterFields?.forEach((field) => {
    filters[field.prop] = field.type === 'dateRange' ? [] : ''
  })
}

const resetAllSearch = () => {
  resetFilters()
  sortField.value = ''
  sortOrder.value = ''
  resetKeywordSearch()
}

const openFeatureDetail = (row: Record<string, any>) => {
  selectedRow.value = row
  featureDetailVisible.value = true
}

const handleSortChange = ({ prop, order }: { prop: string, order: string | null }) => {
  sortField.value = prop || ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  searchData()
}

const openCreate = () => {
  executionMode.value = false
  editingId.value = null
  resetForm()
  void loadFormOptions()
  dialogVisible.value = true
}

const openEdit = (row: Record<string, any>) => {
  executionMode.value = false
  editingId.value = row.id
  resetForm(row)
  void loadFormOptions()
  dialogVisible.value = true
}

const openExecute = (row: Record<string, any>) => {
  executionMode.value = true
  editingId.value = row.id
  resetForm(row)
  void loadFormOptions()
  dialogVisible.value = true
}

const saveData = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateRow(config.value.endpoint, { ...form, id: editingId.value })
    } else {
      await createRow(config.value.endpoint, form)
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

const persistCurrentForm = async () => {
  const payload = { ...form, id: editingId.value }
  const result = editingId.value
    ? await updateRow(config.value.endpoint, payload)
    : await createRow(config.value.endpoint, form)
  return (result as any).data || payload
}

const continueExecution = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  saving.value = true
  try {
    const saved = await persistCurrentForm()
    executionConfig.value = { ...form, ...saved }
    dialogVisible.value = false
    previewPage.value = 1
    await loadPreviewData()
    previewVisible.value = true
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '训练数据预览加载失败')
  } finally {
    saving.value = false
  }
}

const previewPayload = computed<PageRequest>(() => {
  const item = executionConfig.value || {}
  const payload: PageRequest = {
    page: previewPage.value,
    size: item.trainMode === 'RECENT' ? Math.max(Number(item.recentPeriods) || 36, 1) : previewSize.value,
    timeGranularity: item.timeGranularity || undefined,
    regionCode: item.regionCode || undefined,
    industryCode: item.industryCode || undefined,
    customerCode: item.customerCode || undefined,
    sortField: 'statDate',
    sortOrder: item.trainMode === 'RECENT' ? 'desc' : 'asc'
  }
  if (item.trainMode === 'RANGE') {
    payload.statDateStart = item.trainStartDate || undefined
    payload.statDateEnd = item.trainEndDate || undefined
  }
  return payload
})

const loadPreviewData = async () => {
  if (!executionConfig.value) {
    return
  }
  previewLoading.value = true
  try {
    const data = await listPage('/model-train-feature-data', previewPayload.value)
    previewRows.value = data.records
    previewTotal.value = executionConfig.value.trainMode === 'RECENT' ? data.records.length : data.total
  } finally {
    previewLoading.value = false
  }
}

const handlePreviewSizeChange = () => {
  previewPage.value = 1
  void loadPreviewData()
}

const executeTraining = async () => {
  if (!executionConfig.value?.trainCode) {
    ElMessage.error('训练编码不存在')
    return
  }
  executing.value = true
  try {
    await postJson('/model-train-execution/execute', { trainCode: executionConfig.value.trainCode })
    ElMessage.success('训练任务已提交')
    previewVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行训练失败')
  } finally {
    executing.value = false
  }
}

const granularityText = (value: unknown) => {
  if (value === 'DAY') return '日'
  if (value === 'TENDAY') return '旬'
  if (value === 'MONTH') return '月'
  return '-'
}

const removeRow = async (row: Record<string, any>) => {
  await ElMessageBox.confirm('删除后不可恢复，确认删除这条数据？', '删除确认', { type: 'warning' })
  try {
    await deleteRow(config.value.endpoint, row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

watch(() => props.pageConfig, () => {
  keyword.value = ''
  page.value = 1
  resetFilters()
  resetForm()
  void loadFormOptions()
  void loadData()
})

onMounted(() => {
  resetFilters()
  resetForm()
  void loadFormOptions()
  void loadData()
})

defineExpose({ loadData })
</script>

<style scoped>
.crud-page {
  display: flex;
  flex-direction: column;
  gap: var(--app-gap);
}

.search-input {
  width: 260px;
}

.filter-control {
  flex-shrink: 0;
}

.dialog-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
  row-gap: 2px;
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.dialog-form :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: #344054;
  font-weight: 600;
}

.dialog-form :deep(.el-input__wrapper),
.dialog-form :deep(.el-select__wrapper),
.dialog-form :deep(.el-input-number) {
  min-height: var(--app-control-height);
}

.column-header-with-tip {
  cursor: help;
  text-decoration: underline dotted;
  text-underline-offset: 3px;
}

.feature-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #475467;
  font-size: 13px;
}

.feature-summary span {
  border: 1px solid #eaecf0;
  border-radius: 6px;
  padding: 4px 8px;
}

.feature-detail :deep(.el-table .cell) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
}

.preview-summary,
.preview-scope {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.preview-summary span,
.preview-scope span {
  border: 1px solid #eaecf0;
  border-radius: 6px;
  padding: 5px 9px;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
}

.preview-scope span {
  color: #475467;
  font-weight: 600;
}

.preview-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 760px) {
  .search-input {
    width: 100%;
  }

  .filter-control {
    width: 100% !important;
  }

  .dialog-form {
    grid-template-columns: 1fr;
  }
}
</style>
