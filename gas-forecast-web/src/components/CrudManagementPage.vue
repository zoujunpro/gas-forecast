<template>
  <section class="crud-page">
    <PageBreadcrumb /><AppTablePanel :updated-at="updatedAt"
      ><template #filters
        ><el-input
          v-model="keyword"
          clearable
          :prefix-icon="Search"
          :placeholder="config.searchPlaceholder"
          @clear="searchData"
          @keyup.enter="searchData"
        /><template v-for="field in config.filterFields || []" :key="field.prop"
          ><el-date-picker
            v-if="field.type === 'dateRange'"
            v-model="filters[field.prop]"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            @change="searchData" /><el-select
            v-else-if="field.type === 'select'"
            v-model="filters[field.prop]"
            clearable
            filterable
            :placeholder="field.placeholder || field.label"
            @change="searchData"
            ><el-option
              v-for="item in options[field.prop] || field.options || []"
              :key="item.value"
              :label="item.label"
              :value="item.value" /></el-select
          ><el-input
            v-else
            v-model="filters[field.prop]"
            clearable
            :placeholder="field.placeholder || field.label"
            @clear="searchData"
            @keyup.enter="searchData" /></template
        ><el-button type="primary" :icon="Search" @click="searchData">查询</el-button
        ><el-button plain @click="resetAll">重置</el-button></template
      ><template #actions
        ><slot name="table-actions" :reload="loadData" /><PermissionButton
          v-if="!config.readonly"
          type="primary"
          :icon="Plus"
          :permission="config.permissions?.create"
          @click="openCreate"
          >新增</PermissionButton
        ></template
      ><AppTable
        v-loading="loading"
        :data="records"
        :empty-description="`暂无${config.title}`"
        stripe
        border
        @sort-change="sort"
        ><el-table-column type="index" :index="rowIndex" label="序号" width="72" fixed /><el-table-column
          v-for="field in config.tableFields"
          :key="field.prop"
          :prop="field.prop"
          :label="field.label"
          :min-width="field.minWidth || 120"
          :width="field.width"
          :align="field.align"
          :sortable="field.sortable"
          show-overflow-tooltip
          ><template #default="{ row }"><ManagementTableCell :field="field" :row="row" /></template></el-table-column
        ><el-table-column v-if="!config.readonly" label="操作" width="150" fixed="right"
          ><template #default="{ row }"
            ><PermissionButton link type="primary" :permission="config.permissions?.update" @click="openEdit(row)"
              >编辑</PermissionButton
            ><PermissionButton link type="danger" :permission="config.permissions?.delete" @click="remove(row)"
              >删除</PermissionButton
            ></template
          ></el-table-column
        ></AppTable
      ><template #footer
        ><AppPagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="loadData"
          @size-change="handleSizeChange" /></template></AppTablePanel
    ><AppDialog
      v-model="dialogVisible"
      eyebrow="后台数据管理"
      :title="title"
      :width="config.dialogWidth || '680px'"
      align-center
      ><el-form ref="formRef" :model="form" :rules="rules" label-position="top"
        ><el-form-item
          v-for="field in visibleFields"
          :key="field.prop"
          :label="field.label"
          :prop="field.prop"
          :class="{ 'form-item-full': field.fullWidth }"
          ><FormFieldRenderer :field="field" :model="form" :option-map="options" @option-select="selectOption" />
          <p v-if="field.helperText" class="helper">{{ field.helperText }}</p></el-form-item
        ></el-form
      ><template #footer
        ><el-button @click="dialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
      ></AppDialog
    >
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { createRow, deleteRow, listPage, updateRow, type PageRequest } from '@/api/management'
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
const props = defineProps<{ pageConfig: BaseDataPageConfig }>(),
  config = computed(() => props.pageConfig),
  filters = reactive<Record<string, any>>({}),
  form = reactive<Record<string, any>>({}),
  options = reactive<Record<string, Option[]>>({}),
  formRef = ref<FormInstance>(),
  dialogVisible = ref(false),
  editingId = ref<number | null>(null),
  saving = ref(false),
  updatedAt = ref(''),
  sortField = ref(''),
  sortOrder = ref('')
const { loading, keyword, page, size, total, records, loadData, searchData, resetSearch, handleSizeChange, rowIndex } =
  usePageQuery<Record<string, any>>({
    fetcher: async ({ page, size, keyword, signal }) => {
      const payload: PageRequest = {
        page,
        size,
        keyword: keyword || undefined,
        sortField: sortField.value || undefined,
        sortOrder: sortOrder.value || undefined
      }
      for (const f of config.value.filterFields || []) {
        const v = filters[f.prop]
        if (f.type === 'dateRange' && Array.isArray(v)) {
          if (f.startProp) payload[f.startProp] = v[0] || undefined
          if (f.endProp) payload[f.endProp] = v[1] || undefined
        } else if (v !== '' && v != null) payload[f.prop] = v
      }
      const data = await listPage(config.value.endpoint, payload, signal)
      return { records: data.records, total: data.total }
    }
  })
const visibleFields = computed(() =>
    config.value.formFields.filter((f) => !f.visibleWhen || form[f.visibleWhen.prop] === f.visibleWhen.value)
  ),
  title = computed(() => `${editingId.value ? '编辑' : '新增'}${config.value.title.replace('管理', '')}`),
  rules = computed<FormRules>(() =>
    Object.fromEntries(
      config.value.formFields
        .filter((f) => f.required)
        .map((f) => [f.prop, [{ required: true, message: `请填写${f.label}`, trigger: 'blur' }]])
    )
  )
const reset = () => {
    Object.keys(filters).forEach((k) => delete filters[k])
    ;(config.value.filterFields || []).forEach((f) => (filters[f.prop] = f.type === 'dateRange' ? [] : ''))
  },
  resetForm = (row?: Record<string, any>) => {
    Object.keys(form).forEach((k) => delete form[k])
    Object.assign(form, config.value.emptyForm, row || {})
  },
  loadOptions = async () => {
    const fields = [...(config.value.filterFields || []), ...config.value.formFields]
    await Promise.all(
      fields.map(async (f) => {
        if (!f.optionSource) return
        try {
          const d = await listPage(f.optionSource.endpoint, { page: 1, size: f.optionSource.size || 1000 })
          options[f.prop] = d.records.map((r) => ({
            value: r[f.optionSource!.valueProp],
            label:
              f.optionSource!.labelTemplate === 'nameWithCode'
                ? `${r[f.optionSource!.labelProp] || '-'} (${r[f.optionSource!.valueProp] || '-'})`
                : r[f.optionSource!.labelProp],
            raw: r
          }))
        } catch {
          options[f.prop] = []
        }
      })
    )
  },
  resetAll = () => {
    reset()
    sortField.value = ''
    sortOrder.value = ''
    resetSearch()
  },
  sort = ({ prop, order }: { prop: string; order: string | null }) => {
    sortField.value = prop || ''
    sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
    searchData()
  },
  selectOption = (f: BaseDataFieldConfig, o?: Option) =>
    Object.entries(f.fillProps || {}).forEach(([k, v]) => (form[k] = o?.raw?.[v] || '')),
  openCreate = () => {
    editingId.value = null
    resetForm()
    dialogVisible.value = true
  },
  openEdit = (row: Record<string, any>) => {
    editingId.value = row.id
    resetForm(row)
    dialogVisible.value = true
  },
  save = async () => {
    if ((await formRef.value?.validate().catch(() => false)) === false) return
    saving.value = true
    try {
      if (editingId.value) await updateRow(config.value.endpoint, { ...form, id: editingId.value })
      else await createRow(config.value.endpoint, form)
      ElMessage.success('保存成功')
      dialogVisible.value = false
      await loadData()
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      saving.value = false
    }
  },
  remove = async (row: Record<string, any>) => {
    try {
      await ElMessageBox.confirm('删除后不可恢复，确认删除这条数据？', '删除确认', { type: 'warning' })
      await deleteRow(config.value.endpoint, row.id)
      ElMessage.success('删除成功')
      await loadData()
    } catch (e) {
      if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
watch(loading, (v, p) => {
  if (!v && p)
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
})
watch(
  () => props.pageConfig,
  () => {
    reset()
    resetForm()
    void loadOptions()
    void loadData()
  }
)
onMounted(() => {
  reset()
  resetForm()
  void loadOptions()
  void loadData()
})
defineExpose({ loadData })
</script>
<style scoped>
.helper {
  margin: 6px 0 0;
  color: #8492a6;
  font-size: 13px;
}
</style>
