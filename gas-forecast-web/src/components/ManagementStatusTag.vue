<template>
  <el-tag :type="tagType" effect="plain" :class="['management-status-tag', `is-${tone}`]">
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'ManagementStatusTag' })

const props = defineProps<{
  status: string | number
}>()

const labelMap: Record<string, string> = {
  UPLOADED: '已上传',
  PENDING: '等待中',
  PROCESSING: '处理中',
  RUNNING: '进行中',
  SUCCESS: '已完成',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消'
}

const typeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  UPLOADED: 'info',
  PENDING: 'info',
  PROCESSING: 'warning',
  RUNNING: 'warning',
  SUCCESS: 'success',
  COMPLETED: 'success',
  FAILED: 'danger',
  CANCELLED: 'info'
}

const statusKey = computed(() => String(props.status || ''))
const label = computed(() => labelMap[statusKey.value] || statusKey.value || '-')
const tagType = computed(() => typeMap[statusKey.value] || 'info')
const tone = computed(() => {
  if (['SUCCESS', 'COMPLETED'].includes(statusKey.value)) return 'success'
  if (['PROCESSING', 'RUNNING'].includes(statusKey.value)) return 'running'
  if (statusKey.value === 'FAILED') return 'danger'
  return 'neutral'
})
</script>

<style scoped>
.management-status-tag { min-width: 64px; justify-content: center; border: 0; font-weight: 600; }
.management-status-tag.is-success { background: #ecfdf5; color: var(--app-success); }
.management-status-tag.is-running { background: #fff7ed; color: var(--app-warning); }
.management-status-tag.is-danger { background: #fef2f2; color: var(--app-danger); }
.management-status-tag.is-neutral { background: #f1f5f9; color: var(--app-text-muted); }
</style>
