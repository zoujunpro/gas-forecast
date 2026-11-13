<template>
  <el-tag v-if="displayType === 'enabled'" :class="['management-status-tag', value === 1 ? 'is-success' : 'is-neutral']" effect="plain">
    {{ value === 1 ? '启用' : '停用' }}
  </el-tag>
  <ManagementStatusTag v-else-if="displayType === 'status'" :status="value" />
  <el-tag v-else-if="displayType === 'permissionType'" :type="permissionTypeTagType" effect="plain">
    {{ permissionTypeText }}
  </el-tag>
  <span v-else-if="displayType === 'menuIcon'" class="menu-icon-cell">
    <el-icon v-if="value"><component :is="resolveMenuIcon(String(value))" /></el-icon>
    <span>{{ value || '-' }}</span>
  </span>
  <el-tag v-else-if="displayType === 'tag'" :type="tagType" effect="plain">
    {{ mappedValue }}
  </el-tag>
  <span v-else-if="displayType === 'array'">{{ Array.isArray(value) ? value.join('、') : displayValue(value) }}</span>
  <span v-else-if="displayType === 'date' || displayType === 'datetime'">{{ formatDateTime(value, displayType) }}</span>
  <span v-else :class="['cell-text', cellClass]">{{
    displayType === 'hash' ? compactHash(value) : mappedValue
  }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ManagementStatusTag from '@/components/ManagementStatusTag.vue'
import { displayValue } from '@/utils/http'
import { resolveMenuIcon } from '@/utils/menuIcons'
import type { BaseDataFieldConfig, SystemFieldConfig } from '@/views/shared/managementTypes'

const props = defineProps<{
  field: BaseDataFieldConfig | SystemFieldConfig
  row: Record<string, any>
}>()

const value = computed(() => props.row[props.field.prop])
const displayType = computed(() => props.field.displayType || (Array.isArray(value.value) ? 'array' : 'text'))
const mappedValue = computed(() => props.field.enumMap?.[String(value.value)] || displayValue(value.value))
const tagType = computed(() => props.field.tagTypeMap?.[String(value.value)] || 'info')
const permissionTypeText = computed(() => {
  if (value.value === 'DIRECTORY') return '目录'
  if (value.value === 'MENU') return '菜单'
  return '按钮'
})
const permissionTypeTagType = computed(() => {
  if (value.value === 'DIRECTORY') return 'info'
  if (value.value === 'MENU') return 'primary'
  return 'warning'
})
const cellClass = computed(() => ({
  'mono-text': displayType.value === 'hash',
  'cell-primary': /(^|_)(name|title|code)$|customerName|modelName|fileName/i.test(props.field.prop),
  'cell-secondary': displayType.value === 'date' || displayType.value === 'datetime',
  'cell-number': props.field.type === 'number'
}))

const formatDateTime = (rawValue: unknown, type: string) => {
  const text = String(displayValue(rawValue))
  if (text === '-') {
    return text
  }
  if (type === 'date') {
    return text.slice(0, 10)
  }
  return text.length > 19 ? text.slice(0, 19) : text
}

const compactHash = (rawValue: unknown) => {
  const text = String(displayValue(rawValue))
  if (text === '-' || text.length <= 18) {
    return text
  }
  return `${text.slice(0, 10)}...${text.slice(-6)}`
}
</script>

<style scoped>
.menu-icon-cell {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.menu-icon-cell .el-icon {
  display: inline-grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 7px;
  background: var(--app-primary-soft);
  color: var(--el-color-primary);
  font-size: 15px;
}

.management-status-tag { min-width: 56px; justify-content: center; border: 0; font-weight: 600; }
.management-status-tag.is-success { background: #ecfdf5; color: var(--app-success); }
.management-status-tag.is-neutral { background: #f1f5f9; color: var(--app-text-muted); }
.cell-primary { color: var(--app-text); font-weight: 600; }
.cell-secondary { color: var(--app-text-muted); font-size: 13px; }
.cell-number { display: block; font-variant-numeric: tabular-nums; text-align: right; }
</style>
