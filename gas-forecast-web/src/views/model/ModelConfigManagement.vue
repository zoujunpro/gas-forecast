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
          <template #default="{ row }">
            <ManagementTableCell :field="field" :row="row" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" class-name="action-column" label-class-name="action-column">
          <template #default="{ row }">
            <PermissionButton link type="primary" :icon="Setting" permission="model:config:update" @click="openConfig(row)">模型配置</PermissionButton>
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

    <AppDialog v-model="dialogVisible" eyebrow="模型管理" :title="dialogTitle" width="720px" align-center>
      <el-form ref="formRef" class="dialog-form" :model="form" :rules="formRules" label-position="top">
        <el-form-item label="模型编码" prop="configCode">
          <el-input :model-value="form.configCode || '保存后自动生成'" disabled />
        </el-form-item>
        <el-form-item label="模型名称" prop="configName">
          <el-input v-model="form.configName" clearable maxlength="128" />
        </el-form-item>
        <el-form-item label="所属智能体" prop="agentCode">
          <el-select v-model="form.agentCode" class="form-control" @change="handleAgentChange">
            <el-option v-for="item in modelAgentOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isWinterAgent" label="区域" prop="regionCodes">
          <el-select v-model="form.regionCodes" class="form-control" multiple collapse-tags collapse-tags-tooltip filterable @change="handleWinterRegionsChange">
            <el-option label="全部" value="ALL" />
            <el-option v-for="item in regionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="区域" prop="regionCode">
          <el-select v-model="form.regionCodes" class="form-control" multiple collapse-tags collapse-tags-tooltip filterable :disabled="hasCustomerScope" @change="handleNonWinterRegionsChange">
            <el-option label="全部" value="ALL" />
            <el-option v-for="item in regionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isWinterAgent" label="行业" prop="industryCode">
          <el-select v-model="form.industryCodes" class="form-control" multiple collapse-tags collapse-tags-tooltip filterable :disabled="hasCustomerScope" @change="handleNonWinterIndustriesChange">
            <el-option label="全部" value="ALL" />
            <el-option v-for="item in industryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isWinterAgent" label="客户" prop="customerCode">
          <el-select v-model="form.customerCodes" class="form-control" multiple collapse-tags collapse-tags-tooltip filterable @change="handleCustomerChange">
            <el-option label="全部" value="ALL" />
            <el-option v-for="item in filteredCustomerOptions" :key="item.value" :label="item.label" :value="item.value" />
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
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Plus, Search, Setting } from '@element-plus/icons-vue'
import { createRow, deleteRow, listPage, updateRow } from '@/api/management'
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
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  configCode: '',
  configName: '',
  agentCode: 'winter-supply',
  sceneCode: 'WINTER_SUPPLY',
  regionCode: '',
  regionCodes: ['ALL'] as string[],
  industryCode: '',
  industryCodes: ['ALL'] as string[],
  customerCode: '',
  customerCodes: ['ALL'] as string[],
  description: ''
})

interface SelectOption {
  label: string
  value: string
  regionCode?: string
  industryCode?: string
}

const regionOptions = ref<SelectOption[]>([])
const industryOptions = ref<SelectOption[]>([])
const customerOptions = ref<SelectOption[]>([])

const formRules: FormRules = {
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

const dialogTitle = computed(() => editingId.value ? '模型配置' : '新增模型')
const selectedAgent = computed(() => modelAgentOptions.find((item) => item.value === form.agentCode))
const isWinterAgent = computed(() => form.sceneCode === 'WINTER_SUPPLY')
const hasCustomerScope = computed(() => form.customerCodes.some((code) => code !== 'ALL'))
const filteredCustomerOptions = computed(() => {
  return customerOptions.value.filter((item) => {
    const selectedRegionCodes = form.regionCodes.filter((code) => code !== 'ALL')
    const selectedIndustryCodes = form.industryCodes.filter((code) => code !== 'ALL')
    if (selectedRegionCodes.length && !selectedRegionCodes.includes(item.regionCode || '')) return false
    if (selectedIndustryCodes.length && !selectedIndustryCodes.includes(item.industryCode || '')) return false
    return true
  })
})

const syncSceneCode = () => {
  form.sceneCode = selectedAgent.value?.sceneCode || ''
}

const handleAgentChange = () => {
  syncSceneCode()
  if (isWinterAgent.value) {
    form.regionCodes = ['ALL']
    form.regionCode = ''
  } else {
    form.regionCodes = ['ALL']
  }
  form.industryCodes = ['ALL']
  form.customerCodes = ['ALL']
  form.industryCode = ''
  form.customerCode = ''
}

const resetForm = (row?: Record<string, any>) => {
  editingId.value = row?.id ?? null
  form.configCode = row?.configCode || ''
  form.configName = row?.configName || ''
  form.agentCode = row?.agentCode || 'winter-supply'
  form.sceneCode = row?.sceneCode || selectedAgent.value?.sceneCode || 'WINTER_SUPPLY'
  form.regionCode = row?.regionCode || ''
  form.regionCodes = row?.regionCodes?.length ? row.regionCodes : ['ALL']
  form.industryCode = row?.industryCode || ''
  form.industryCodes = row?.industryCodes?.length ? row.industryCodes : ['ALL']
  form.customerCode = row?.customerCode || ''
  form.customerCodes = row?.customerCodes?.length ? row.customerCodes : ['ALL']
  form.description = row?.description || ''
  syncSceneCode()
  if (isWinterAgent.value && !form.regionCodes.length) {
    form.regionCodes = ['ALL']
  }
  if (!isWinterAgent.value) {
    form.regionCodes = row?.regionCodes?.length ? row.regionCodes : ['ALL']
    form.industryCodes = row?.industryCodes?.length ? row.industryCodes : ['ALL']
    form.customerCodes = row?.customerCodes?.length ? row.customerCodes : ['ALL']
  }
}

const loadScopeOptions = async () => {
  const [regions, industries, customers] = await Promise.all([
    listPage('/base-region', { page: 1, size: 200 }),
    listPage('/base-industry', { page: 1, size: 200 }),
    listPage('/base-customer', { page: 1, size: 200 })
  ])
  regionOptions.value = regions.records.map((item: Record<string, any>) => ({
    label: item.regionName || item.regionCode,
    value: item.regionCode
  })).filter((item: SelectOption) => item.value)
  industryOptions.value = industries.records.map((item: Record<string, any>) => ({
    label: item.industryName || item.industryCode,
    value: item.industryCode
  })).filter((item: SelectOption) => item.value)
  customerOptions.value = customers.records.map((item: Record<string, any>) => ({
    label: item.customerName || item.customerCode,
    value: item.customerCode,
    regionCode: item.regionCode,
    industryCode: item.industryCode
  })).filter((item: SelectOption) => item.value)
}

const handleWinterRegionsChange = (values: string[]) => {
  if (!values.length || values[values.length - 1] === 'ALL') {
    form.regionCodes = ['ALL']
    return
  }
  form.regionCodes = values.filter((value) => value !== 'ALL')
}

const handleNonWinterRegionsChange = (values: string[]) => {
  if (!values.length || values[values.length - 1] === 'ALL') {
    form.regionCodes = ['ALL']
    return
  }
  form.regionCodes = values.filter((value) => value !== 'ALL')
}

const handleNonWinterIndustriesChange = (values: string[]) => {
  if (!values.length || values[values.length - 1] === 'ALL') {
    form.industryCodes = ['ALL']
    return
  }
  form.industryCodes = values.filter((value) => value !== 'ALL')
}

const handleCustomerChange = (values: string[]) => {
  if (!values.length || values[values.length - 1] === 'ALL') {
    form.customerCodes = ['ALL']
    return
  }
  form.customerCodes = values.filter((value) => value !== 'ALL')
  const selectedCustomers = customerOptions.value.filter((item) => form.customerCodes.includes(item.value))
  const regionCodes = Array.from(new Set(selectedCustomers.map((item) => item.regionCode).filter(Boolean))) as string[]
  const industryCodes = Array.from(new Set(selectedCustomers.map((item) => item.industryCode).filter(Boolean))) as string[]
  form.regionCodes = regionCodes.length ? regionCodes : ['ALL']
  form.industryCodes = industryCodes.length ? industryCodes : ['ALL']
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openConfig = (row: Record<string, any>) => {
  resetForm(row)
  dialogVisible.value = true
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
  if (isWinterAgent.value) {
    const regionCodes = form.regionCodes.includes('ALL') ? [] : form.regionCodes
    return {
      ...form,
      regionCode: '',
      regionCodes,
      industryCode: '',
      customerCode: ''
    }
  }
  return {
    ...form,
    regionCode: '',
    industryCode: '',
    customerCode: '',
    regionCodes: hasCustomerScope.value ? [] : form.regionCodes.filter((code) => code !== 'ALL'),
    industryCodes: hasCustomerScope.value ? [] : form.industryCodes.filter((code) => code !== 'ALL'),
    customerCodes: hasCustomerScope.value ? form.customerCodes.filter((code) => code !== 'ALL') : []
  }
}

const removeRow = async (row: Record<string, any>) => {
  await ElMessageBox.confirm('删除模型配置后，对应作用范围配置也会同步删除，确认继续？', '删除确认', { type: 'warning' })
  try {
    await deleteRow(endpoint, row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(() => {
  void loadScopeOptions()
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

.dialog-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.dialog-form :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: #344054;
  font-weight: 600;
}

@media (max-width: 760px) {
  .search-input {
    width: 100%;
  }

  .dialog-form {
    grid-template-columns: 1fr;
  }
}
</style>
