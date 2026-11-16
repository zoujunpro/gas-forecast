<template>
  <template v-for="(block, index) in blocks" :key="index">
    <MarkdownBlock v-if="block.type === 'MARKDOWN'" :data="block.data" />
    <TableBlock v-else-if="block.type === 'TABLE' && hasTable(block.data)" :data="block.data" />
    <ChartBlock v-else-if="block.type === 'CHART' && Array.isArray(block.data.rows)" :data="block.data" />
    <a v-else-if="block.type === 'RESULT_LINK' && safeLink(block.data.url)" :href="block.data.url">{{ block.data.title || '查看结果' }}</a>
    <AiExecutionTask v-else-if="block.type === 'TASK_CARD' && block.data.taskNo" :task-no="block.data.taskNo" :compact="compactTasks"
      @forecast-completed="value => emit('forecast-completed', value)"
      @batch-detail-selected="value => emit('batch-detail-selected', value)"
      @analysis-question="value => emit('analysis-question', value)"
      @content-rendered="emit('content-rendered')" />
  </template>
</template>
<script setup lang="ts">
import type { AiChatContentBlock } from '@/types'
import MarkdownBlock from './MarkdownBlock.vue'
import AiExecutionTask from '@/views/ai/AiExecutionTask.vue'
import TableBlock from './TableBlock.vue'
import ChartBlock from './ChartBlock.vue'
const emit = defineEmits<{
  'forecast-completed': [value: any]
  'batch-detail-selected': [value: any]
  'analysis-question': [value: any]
  'content-rendered': []
}>()
defineProps<{ blocks: AiChatContentBlock[]; compactTasks?: boolean }>()
const safeLink = (value: unknown) => typeof value === 'string' && /^\/(?![\/\\])[^\\]*$/.test(value)
const hasTable = (data: Record<string, any>) => Array.isArray(data.rows) || Array.isArray(data.data?.records)
</script>

