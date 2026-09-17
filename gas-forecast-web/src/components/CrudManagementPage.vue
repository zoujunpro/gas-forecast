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
        <el-button type="primary" :icon="Search" @click="searchData">查询</el-button>
        <el-button type="info" plain @click="resetSearch">重置</el-button>
      </template>

      <template #actions>
        <slot name="table-actions" :reload="loadData" />
        <PermissionButton type="primary" :icon="Plus" :permission="config.permissions?.create" @click="openCreate">新增</PermissionButton>
      </template>

      <AppTable v-loading="loading" :data="records" stripe border>
        <el-table-column type="index" :index="rowIndex" label="序号" width="72" fixed class-name="id-column" label-class-name="id-column" />
        <el-table-column
          v-for="field in config.tableFields"
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
        <el-table-column label="操作" width="150" fixed="right" class-name="action-column" label-class-name="action-column">
          <template #default="{ row }">
            <PermissionButton link type="primary" :permission="config.permissions?.update" @click="openEdit(row)">编辑</PermissionButton>
            <PermissionButton link type="danger" :permission="config.permissions?.delete" @click="removeRow(row)">删除</PermissionButton>
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
        <el-form-item v-for="field in config.formFields" :key="field.prop" :label="field.label" :prop="field.prop">
          <FormFieldRenderer :field="field" :model="form" />
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { createRow, deleteRow, listPage, updateRow } from '@/api/management'
import { usePageQuery } from '@/composables/usePageQuery'
import AppDialog from '@/components/AppDialog.vue'
import AppPagination from '@/components/AppPagination.vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'
import ManagementTableCell from '@/components/ManagementTableCell.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import type { BaseDataPageConfig } from '@/views/shared/managementTypes'

const props = defineProps<{
  pageConfig: BaseDataPageConfig
}>()

const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<Record<string, any>>({})

const config = computed(() => props.pageConfig)
const dialogTitle = computed(() => `${editingId.value ? '编辑' : '新增'}${config.value.title.replace('管理', '')}`)
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  config.value.formFields.forEach((field) => {
    const fieldRules = []
    if (field.required) {
      fieldRules.push({ required: true, message: `请输入${field.label}`, trigger: field.type === 'select' ? 'change' : 'blur' })
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
  resetSearch,
  handleSizeChange,
  rowIndex
} = usePageQuery<Record<string, any>>({
  errorMessage: '列表加载失败',
  fetcher: ({ page, size, keyword }) => listPage(config.value.endpoint, { page, size, keyword: keyword || undefined })
})

const resetForm = (row?: Record<string, any>) => {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, row ? { ...row } : { ...config.value.emptyForm })
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: Record<string, any>) => {
  editingId.value = row.id
  resetForm(row)
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
  resetForm()
  void loadData()
})

onMounted(() => {
  resetForm()
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

@media (max-width: 760px) {
  .search-input {
    width: 100%;
  }

  .dialog-form {
    grid-template-columns: 1fr;
  }
}
</style>
