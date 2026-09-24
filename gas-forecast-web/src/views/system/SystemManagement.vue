<template>
  <section class="system-page">
    <PageBreadcrumb />

    <AppTablePanel>
      <template #filters>
        <el-input
          v-model="keyword"
          class="search-input"
          clearable
          :prefix-icon="Search"
          :placeholder="config.placeholder"
          @clear="searchData"
          @keyup.enter="searchData"
        />
        <el-select
          v-if="config.mode === 'permissions'"
          v-model="permissionKindFilter"
          class="filter-select"
          @change="searchData"
        >
          <el-option label="全部类型" value="" />
          <el-option label="目录" value="DIRECTORY" />
          <el-option label="菜单" value="MENU" />
        </el-select>
        <el-select
          v-if="config.mode === 'permissions'"
          v-model="statusFilter"
          class="filter-select"
          @change="searchData"
        >
          <el-option label="全部状态" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="searchData">查询</el-button>
        <el-button type="info" plain @click="resetAllFilters">重置</el-button>
      </template>
      <template #actions>
        <el-button v-if="config.mode === 'permissions'" plain :icon="Expand" @click="setTreeExpanded(true)"
          >展开全部</el-button
        >
        <el-button v-if="config.mode === 'permissions'" plain :icon="Fold" @click="setTreeExpanded(false)"
          >折叠全部</el-button
        >
        <PermissionButton type="primary" :icon="Plus" :permission="config.permissions?.create" @click="openCreate">{{
          config.mode === 'permissions' ? '新增菜单' : '新增'
        }}</PermissionButton>
      </template>
      <AppTable
        :key="tableKey"
        v-loading="loading"
        :data="records"
        :row-key="rowKey"
        :tree-props="{ children: 'children' }"
        :default-expand-all="treeExpanded"
        stripe
        border
      >
        <el-table-column
          v-if="config.mode !== 'permissions'"
          type="index"
          :index="rowIndex"
          label="序号"
          width="72"
          fixed
          class-name="id-column"
          label-class-name="id-column"
        />
        <el-table-column
          v-for="field in config.tableFields"
          :key="field.prop"
          :prop="field.prop"
          :label="field.label"
          :min-width="field.minWidth || 120"
        >
          <template #header>
            <el-tooltip v-if="field.tooltip" :content="field.tooltip" placement="top">
              <span class="column-header-with-tip">{{ field.label }}</span>
            </el-tooltip>
            <span v-else>{{ field.label }}</span>
          </template>
          <template #default="{ row }">
            <template v-if="config.mode === 'permissions' && field.prop === 'permissionName'">
              <span class="menu-name-cell">
                <el-icon class="menu-kind-icon">
                  <Folder v-if="derivePermissionKind(row) === 'DIRECTORY'" />
                  <Document v-else-if="derivePermissionKind(row) === 'MENU'" />
                  <Pointer v-else />
                </el-icon>
                <span>{{ row.permissionName || '-' }}</span>
              </span>
            </template>
            <template v-else-if="config.mode === 'permissions' && field.prop === 'status'">
              <span class="status-cell">
                <el-switch
                  :model-value="row.status"
                  :active-value="1"
                  :inactive-value="0"
                  :disabled="!hasPermission(config.permissions?.update)"
                  size="small"
                  @change="togglePermissionStatus(row, Number($event))"
                />
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" round>
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </span>
            </template>
            <ManagementTableCell v-else :field="field" :row="row" />
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="300"
          fixed="right"
          class-name="action-column"
          label-class-name="action-column"
        >
          <template #default="{ row }">
            <template v-if="config.mode === 'departments'">
              <PermissionButton link type="primary" :permission="config.permissions?.update" @click="moveDepartment(row, -1)"
                >上移</PermissionButton
              >
              <PermissionButton link type="primary" :permission="config.permissions?.update" @click="moveDepartment(row, 1)"
                >下移</PermissionButton
              >
            </template>
            <template v-if="config.mode === 'permissions' && row.permissionType !== 'BUTTON'">
              <PermissionButton link type="primary" :permission="config.permissions?.update" @click="movePermission(row, -1)"
                >上移</PermissionButton
              >
              <PermissionButton link type="primary" :permission="config.permissions?.update" @click="movePermission(row, 1)"
                >下移</PermissionButton
              >
            </template>
            <PermissionButton
              v-if="config.mode === 'permissions' && row.permissionType === 'DIRECTORY'"
              link
              type="primary"
              :permission="config.permissions?.create"
              @click="openCreateChild(row)"
            >
              新增子菜单
            </PermissionButton>
            <PermissionButton
              v-if="config.mode === 'permissions' && row.permissionType === 'MENU'"
              link
              type="success"
              :permission="config.permissions?.update"
              @click="openPermissionConfig(row)"
              >权限配置</PermissionButton
            >
            <PermissionButton link type="primary" :permission="config.permissions?.update" @click="openEdit(row)"
              >编辑</PermissionButton
            >
            <PermissionButton link type="danger" :permission="config.permissions?.delete" @click="removeRow(row)"
              >删除</PermissionButton
            >
          </template>
        </el-table-column>
      </AppTable>

      <template v-if="config.paged" #footer>
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

    <el-drawer v-model="permissionDrawerVisible" title="按钮权限配置" size="520px">
      <div v-if="selectedPermissionMenu" class="permission-config-panel">
        <div class="permission-menu-summary">
          <span>当前菜单</span>
          <strong>{{ selectedPermissionMenu.permissionName }}</strong>
          <code>{{ selectedPermissionMenu.path || '-' }}</code>
        </div>
        <div class="permission-config-head">
          <div>
            <strong>操作权限</strong>
            <p>按钮权限仅用于控制页面操作，不显示在菜单树中。</p>
          </div>
          <PermissionButton
            type="primary"
            :icon="Plus"
            :permission="config.permissions?.create"
            @click="openCreateButton"
            >新增权限</PermissionButton
          >
        </div>
        <el-empty v-if="!buttonPermissions.length" description="暂未配置按钮权限" />
        <div v-else class="button-permission-list">
          <div v-for="item in buttonPermissions" :key="item.id" class="button-permission-item">
            <div class="button-permission-main">
              <strong>{{ item.permissionName }}</strong>
              <code>{{ item.perms || '-' }}</code>
            </div>
            <span class="status-cell">
              <el-switch
                :model-value="item.status"
                :active-value="1"
                :inactive-value="0"
                :disabled="!hasPermission(config.permissions?.update)"
                size="small"
                @change="togglePermissionStatus(item, Number($event))"
              />
              <el-tag :type="item.status === 1 ? 'success' : 'info'" effect="light">
                {{ item.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </span>
            <PermissionButton link type="primary" :permission="config.permissions?.update" @click="openEdit(item)"
              >编辑</PermissionButton
            >
            <PermissionButton link type="danger" :permission="config.permissions?.delete" @click="removeRow(item)"
              >删除</PermissionButton
            >
          </div>
        </div>
      </div>
    </el-drawer>

    <AppDialog v-model="dialogVisible" :eyebrow="config.title" :title="dialogTitle" width="720px" align-center>
      <div v-if="isButtonPermissionForm" class="fixed-permission-context">
        <div><span>所属菜单</span><strong>{{ selectedPermissionMenu?.permissionName }}</strong></div>
        <div><span>权限类型</span><el-tag type="primary" effect="light">按钮</el-tag></div>
      </div>
      <el-form ref="formRef" class="dialog-form" :model="form" :rules="formRules" label-position="top">
        <el-form-item v-for="field in visibleFormFields" :key="field.prop" :label="field.label" :prop="field.prop">
          <FormFieldRenderer
            :field="field"
            :model="form"
            :option-map="optionMap"
            :tree-data="treeSelectData(field.optionKey)"
          >
            <div class="permission-tree-toolbar">
              <span class="permission-tree-count">已选 {{ selectedPermissionCount }} 项</span>
              <div class="permission-tree-actions">
                <el-button type="primary" plain size="small" :icon="Check" @click="checkAllPermissions">全选</el-button>
                <el-button type="warning" plain size="small" :icon="RefreshLeft" @click="clearPermissionChecks"
                  >清空</el-button
                >
                <el-button type="info" plain size="small" :icon="Expand" @click="expandPermissionTree(true)"
                  >全部展开</el-button
                >
                <el-button type="info" plain size="small" :icon="Fold" @click="expandPermissionTree(false)"
                  >全部收起</el-button
                >
              </div>
            </div>
            <el-tree
              ref="permissionTreeRef"
              class="permission-tree"
              :data="permissionTree"
              show-checkbox
              node-key="id"
              default-expand-all
              :render-after-expand="false"
              :props="{ label: 'permissionName', children: 'children' }"
              @check="refreshSelectedPermissionCount"
            />
          </FormFieldRenderer>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="info" plain @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRow">保存</el-button>
      </template>
    </AppDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, ElTree, type FormInstance, type FormRules } from 'element-plus'
import { Check, Document, Expand, Folder, Fold, Plus, Pointer, RefreshLeft, Search } from '@element-plus/icons-vue'
import AppDialog from '@/components/AppDialog.vue'
import AppPagination from '@/components/AppPagination.vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'
import ManagementTableCell from '@/components/ManagementTableCell.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { usePageQuery } from '@/composables/usePageQuery'
import { deleteRow, listAll, listPage, postJson, saveRow as saveRecord } from '@/api/management'
import { hasPermission, refreshProfile } from '@/utils/auth'
import type { Option, SystemPageConfig as PageConfig } from '../shared/managementTypes'

const props = defineProps<{
  pageConfig: PageConfig
}>()

const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const flatRecords = ref<Record<string, any>[]>([])
const allPermissionRecords = ref<Record<string, any>[]>([])
const permissionKindFilter = ref('')
const statusFilter = ref<number | ''>('')
const treeExpanded = ref(true)
const tableKey = ref(0)
const form = reactive<Record<string, any>>({})
const optionMap = reactive<Record<string, Option[]>>({ roles: [], departments: [] })
const permissionTree = ref<Record<string, any>[]>([])
const departmentTree = ref<Record<string, any>[]>([])
const permissionTreeRef = ref<InstanceType<typeof ElTree> | InstanceType<typeof ElTree>[]>()
const selectedPermissionCount = ref(0)
const permissionDrawerVisible = ref(false)
const selectedPermissionMenu = ref<Record<string, any> | null>(null)
const buttonPermissions = computed(() => {
  if (!selectedPermissionMenu.value) return []
  return allPermissionRecords.value
    .filter(
      (item) => item.permissionType === 'BUTTON' && item.parentId === selectedPermissionMenu.value?.id
    )
    .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0) || a.id - b.id)
})

const config = computed(() => props.pageConfig)
const isButtonPermissionForm = computed(
  () => config.value.mode === 'permissions' && form.uiPermissionKind === 'BUTTON' && Boolean(selectedPermissionMenu.value)
)
const dialogTitle = computed(() => {
  if (isButtonPermissionForm.value) {
    return `${editingId.value ? '编辑' : '新增'}按钮权限`
  }
  return `${editingId.value ? '编辑' : '新增'}${config.value.title}`
})
const visibleFormFields = computed(() =>
  config.value.formFields.filter((field) => {
    if (isButtonPermissionForm.value && (field.prop === 'uiPermissionKind' || field.prop === 'parentId')) {
      return false
    }
    if (config.value.mode === 'permissions' && field.prop === 'status') {
      return false
    }
    if (field.prop === 'icon') {
      return form.uiPermissionKind === 'DIRECTORY'
    }
    return true
  })
)
const rowKey = (row: Record<string, any>) => row.id
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  config.value.formFields.forEach((field) => {
    const fieldRules = []
    if (field.required) {
      fieldRules.push({
        required: true,
        message: `请输入${field.label}`,
        trigger: field.type === 'select' || field.type === 'treeSelect' ? 'change' : 'blur'
      })
    }
    if (field.maxLength) {
      fieldRules.push({
        max: field.maxLength,
        message: `${field.label}不能超过${field.maxLength}个字符`,
        trigger: 'blur'
      })
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
const { loading, keyword, page, size, total, records, loadData, searchData, resetSearch, handleSizeChange, rowIndex } =
  usePageQuery<Record<string, any>>({
    errorMessage: '加载失败',
    fetcher: async ({ page, size, keyword, signal }) => {
      if (config.value.paged) {
        const result = await listPage(config.value.endpoint, { page, size, keyword: keyword || undefined }, signal)
        return {
          records: result.records,
          total: result.total
        }
      }

      const sourceRecords = await listAll(config.value.endpoint, keyword)
      if (config.value.mode === 'permissions') {
        allPermissionRecords.value = keyword ? await listAll(config.value.endpoint) : sourceRecords
      }
      flatRecords.value = config.value.mode === 'permissions' ? filterPermissionRows(sourceRecords) : sourceRecords
      const treeRecords = buildTree(flatRecords.value)
      if (config.value.mode === 'departments') {
        departmentTree.value = buildTree(flatRecords.value)
      }
      return {
        records: treeRecords,
        total: flatRecords.value.length
      }
    }
  })

const loadOptions = async () => {
  const response = await fetch('/system/options')
  const result = await response.json()
  if (response.ok && result.code === '0000') {
    optionMap.roles = result.data.roles || []
    optionMap.departments = result.data.departments || []
    permissionTree.value = buildTree(result.data.permissions || [])
  }
}

const openCreate = async () => {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
  await nextTick()
  getPermissionTree()?.setCheckedKeys([])
  refreshSelectedPermissionCount()
}

const openCreateChild = async (row: Record<string, any>) => {
  editingId.value = null
  resetForm()
  form.parentId = row.id
  form.uiPermissionKind = 'MENU'
  form.permissionType = form.uiPermissionKind
  dialogVisible.value = true
  await nextTick()
  getPermissionTree()?.setCheckedKeys([])
  refreshSelectedPermissionCount()
}

const openPermissionConfig = (row: Record<string, any>) => {
  selectedPermissionMenu.value = row
  permissionDrawerVisible.value = true
}

const openCreateButton = async () => {
  if (!selectedPermissionMenu.value) return
  editingId.value = null
  resetForm()
  form.parentId = selectedPermissionMenu.value.id
  form.uiPermissionKind = 'BUTTON'
  form.permissionType = 'BUTTON'
  dialogVisible.value = true
}

const openEdit = async (row: Record<string, any>) => {
  editingId.value = row.id
  resetForm(row)
  dialogVisible.value = true
  await nextTick()
  getPermissionTree()?.setCheckedKeys(row.permissionIds || [])
  refreshSelectedPermissionCount()
}

const togglePermissionStatus = async (row: Record<string, any>, status: number) => {
  if (!hasPermission(config.value.permissions?.update) || row.status === status) return
  try {
    await saveRecord(config.value.endpoint, { ...row, status })
    row.status = status
    await refreshProfile()
    await loadOptions()
    await loadData()
    ElMessage.success(status === 1 ? '已启用' : '已停用')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  }
}

const saveRow = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (config.value.mode === 'roles') {
      const permissionTree = getPermissionTree()
      const checkedKeys = permissionTree?.getCheckedKeys(false) || []
      const halfCheckedKeys = permissionTree?.getHalfCheckedKeys() || []
      payload.permissionIds = Array.from(new Set([...checkedKeys, ...halfCheckedKeys]))
    } else if (config.value.mode === 'permissions') {
      payload.permissionType = payload.uiPermissionKind || payload.permissionType || 'MENU'
      if (payload.uiPermissionKind === 'DIRECTORY') {
        payload.path = null
        payload.component = null
        payload.perms = null
      } else if (payload.uiPermissionKind === 'MENU') {
        payload.icon = null
      } else if (payload.uiPermissionKind === 'BUTTON') {
        payload.path = null
        payload.component = null
        payload.icon = null
      }
      delete payload.uiPermissionKind
    }
    await saveRecord(config.value.endpoint, payload)
    if (config.value.mode === 'permissions') {
      await refreshProfile()
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadOptions()
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

const removeRow = async (row: Record<string, any>) => {
  const hasPermissionChildren =
    config.value.mode === 'permissions' && allPermissionRecords.value.some((item) => item.parentId === row.id)
  if (hasPermissionChildren) {
    ElMessage.warning('请先删除子菜单或按钮')
    return
  }
  await ElMessageBox.confirm(
    `确认删除 ${row.permissionName || row.departmentName || row.roleName || row.username}？`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteRow(config.value.endpoint, row.id)
  ElMessage.success('删除成功')
  await loadOptions()
  await loadData()
}

const resetAllFilters = () => {
  if (config.value.mode === 'permissions') {
    permissionKindFilter.value = ''
    statusFilter.value = ''
  }
  resetSearch()
}

const setTreeExpanded = (expanded: boolean) => {
  treeExpanded.value = expanded
  tableKey.value += 1
}

const moveDepartment = async (row: Record<string, any>, direction: -1 | 1) => {
  const siblings = flatRecords.value
    .filter((item) => (item.parentId || 0) === (row.parentId || 0))
    .sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0) || a.id - b.id)
  const index = siblings.findIndex((item) => item.id === row.id)
  const target = siblings[index + direction]
  if (!target) {
    ElMessage.info(direction < 0 ? '已经是同级第一个部门' : '已经是同级最后一个部门')
    return
  }
  const currentSort = row.sortNo || 0
  const targetSort = target.sortNo || 0
  await saveDepartmentSort({ ...row, sortNo: targetSort })
  await saveDepartmentSort({ ...target, sortNo: currentSort })
  await loadOptions()
  await loadData()
}

const saveDepartmentSort = async (row: Record<string, any>) => {
  await saveRecord('/system/departments', row)
}

const movePermission = async (row: Record<string, any>, direction: -1 | 1) => {
  const result = await postJson('/system/permissions/move', { id: row.id, direction })
  if (result.data?.moved === false) {
    ElMessage.info(direction < 0 ? '已经是同级第一个' : '已经是同级最后一个')
    return
  }
  await loadOptions()
  await loadData()
}

const getPermissionTree = () => {
  const tree = permissionTreeRef.value
  return Array.isArray(tree) ? tree[0] : tree
}

const collectPermissionIds = (nodes: Record<string, any>[] = []) => {
  const ids: number[] = []
  const visit = (items: Record<string, any>[]) => {
    items.forEach((item) => {
      ids.push(item.id)
      if (item.children?.length) {
        visit(item.children)
      }
    })
  }
  visit(nodes)
  return ids
}

const refreshSelectedPermissionCount = () => {
  const tree = getPermissionTree()
  selectedPermissionCount.value = tree ? tree.getCheckedKeys(false).length : 0
}

const checkAllPermissions = () => {
  getPermissionTree()?.setCheckedKeys(collectPermissionIds(permissionTree.value))
  refreshSelectedPermissionCount()
}

const clearPermissionChecks = () => {
  getPermissionTree()?.setCheckedKeys([])
  refreshSelectedPermissionCount()
}

const expandPermissionTree = (expanded: boolean) => {
  const tree = getPermissionTree()
  if (!tree) {
    return
  }
  collectPermissionIds(permissionTree.value).forEach((id) => {
    const node = tree.getNode(id) as any
    if (node) {
      node.expanded = expanded
    }
  })
}

const resetForm = (row?: Record<string, any>) => {
  Object.keys(form).forEach((key) => delete form[key])
  const data: Record<string, any> = row ? { ...row, password: '' } : { ...config.value.emptyForm }
  if (config.value.mode === 'permissions') {
    data.uiPermissionKind = derivePermissionKind(data)
  }
  Object.assign(form, data)
}

const buildTree = (items: Record<string, any>[]) => {
  const map = new Map<number, Record<string, any>>()
  const roots: Record<string, any>[] = []
  items.forEach((item) => map.set(item.id, { ...item, children: [] }))
  map.forEach((item) => {
    const parentId = item.parentId || 0
    const parent = map.get(parentId)
    if (parent) parent.children.push(item)
    else roots.push(item)
  })
  const clean = (nodes: Record<string, any>[]) => {
    nodes.forEach((node) => {
      clean(node.children)
      if (!node.children.length) delete node.children
    })
    return nodes
  }
  return clean(roots)
}

const filterPermissionRows = (items: Record<string, any>[]) => {
  const kind = permissionKindFilter.value
  const status = statusFilter.value
  return items.filter((item) => {
    if (item.permissionType === 'BUTTON') {
      return false
    }
    if (kind && derivePermissionKind(item) !== kind) {
      return false
    }
    if (status !== '' && item.status !== status) {
      return false
    }
    return true
  })
}

const treeSelectData = (key?: string) => {
  if (key === 'permissions') return permissionTree.value
  if (key === 'departments') return departmentTree.value
  return []
}

const derivePermissionKind = (row: Record<string, any>) => {
  if (row.permissionType === 'BUTTON') return 'BUTTON'
  if (row.permissionType === 'DIRECTORY') return 'DIRECTORY'
  if (row.path || row.component) return 'MENU'
  return 'DIRECTORY'
}

watch(
  () => props.pageConfig,
  async () => {
    keyword.value = ''
    page.value = 1
    await loadOptions()
    await loadData()
  }
)

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
.system-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.search-input {
  width: 260px;
}

.filter-select {
  width: 140px;
}

.form-control {
  width: 100%;
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
  min-height: 38px;
}

.dialog-form :deep(.permission-tree-field) {
  grid-column: 1 / -1;
}

.dialog-form :deep(.el-form-item:has(.permission-tree-field)) {
  grid-column: 1 / -1;
}

.permission-tree-field {
  width: 100%;
}

.permission-tree-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.permission-tree-count {
  color: #667085;
  font-size: 13px;
}

.permission-tree-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.permission-tree-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.permission-tree {
  width: 100%;
  max-height: 420px;
  overflow: auto;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e6eaf0;
  border-radius: 8px;
}

.permission-tree :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: 6px;
}

.permission-tree :deep(.el-tree-node__content:hover) {
  background: #eaf6ff;
}

.column-header-with-tip {
  cursor: help;
}

.menu-name-cell,
.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.menu-kind-icon {
  color: #667085;
  font-size: 15px;
}

.permission-config-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.fixed-permission-context {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 18px;
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f5f9ff;
}

.fixed-permission-context > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fixed-permission-context span {
  color: #86909c;
  font-size: 13px;
}

.permission-menu-summary {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 12px;
  padding: 16px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f5f9ff;
}

.permission-menu-summary span {
  color: #86909c;
  font-size: 13px;
}

.permission-menu-summary code {
  grid-column: 1 / -1;
  color: #4e5969;
}

.permission-config-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.permission-config-head p {
  margin-top: 4px;
  color: #86909c;
  font-size: 13px;
}

.button-permission-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.button-permission-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #e5eaf0;
  border-radius: 8px;
}

.button-permission-main {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.button-permission-main code {
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-page :deep(.el-table__placeholder) {
  position: relative;
}

.system-page :deep(.el-table__placeholder::before) {
  position: absolute;
  top: -13px;
  bottom: -13px;
  left: 50%;
  width: 1px;
  background: #d6e0ea;
  content: '';
}

.system-page :deep(.el-table__indent) {
  position: relative;
}

.system-page :deep(.el-table__indent::after) {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 12px;
  height: 1px;
  background: #d6e0ea;
  content: '';
}

@media (max-width: 760px) {
  .search-input,
  .filter-select {
    width: 100%;
  }

  .dialog-form {
    grid-template-columns: 1fr;
  }

  .permission-tree-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
