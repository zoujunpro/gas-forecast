<template>
  <el-tag :type="tagType" effect="plain">
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
  PROCESSING: '处理中',
  SUCCESS: '处理成功',
  FAILED: '处理失败'
}

const typeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  UPLOADED: 'info',
  PROCESSING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger'
}

const statusKey = computed(() => String(props.status || ''))
const label = computed(() => labelMap[statusKey.value] || statusKey.value || '-')
const tagType = computed(() => typeMap[statusKey.value] || 'info')
</script>
