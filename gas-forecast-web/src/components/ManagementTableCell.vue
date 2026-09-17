<template>
  <el-tag v-if="displayType === 'enabled'" :type="value === 1 ? 'success' : 'info'" effect="plain">
    {{ value === 1 ? '启用' : '停用' }}
  </el-tag>
  <ManagementStatusTag v-else-if="displayType === 'status'" :status="value" />
  <el-tag v-else-if="displayType === 'permissionType'" :type="value === 'MENU' ? 'primary' : 'warning'" effect="plain">
    {{ value === 'MENU' ? '菜单' : '按钮' }}
  </el-tag>
  <el-tag v-else-if="displayType === 'tag'" :type="tagType" effect="plain">
    {{ mappedValue }}
  </el-tag>
  <span v-else-if="displayType === 'array'">{{ Array.isArray(value) ? value.join('、') : displayValue(value) }}</span>
  <span v-else-if="displayType === 'date' || displayType === 'datetime'">{{ formatDateTime(value, displayType) }}</span>
  <span v-else :class="['cell-text', { 'mono-text': displayType === 'hash' }]">{{ displayType === 'hash' ? compactHash(value) : mappedValue }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ManagementStatusTag from '@/components/ManagementStatusTag.vue'
import { displayValue } from '@/utils/http'
import type { BaseDataFieldConfig, SystemFieldConfig } from '@/views/shared/managementTypes'

const props = defineProps<{
  field: BaseDataFieldConfig | SystemFieldConfig
  row: Record<string, any>
}>()

const value = computed(() => props.row[props.field.prop])
const displayType = computed(() => props.field.displayType || (Array.isArray(value.value) ? 'array' : 'text'))
const mappedValue = computed(() => props.field.enumMap?.[String(value.value)] || displayValue(value.value))
const tagType = computed(() => props.field.tagTypeMap?.[String(value.value)] || 'info')

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
