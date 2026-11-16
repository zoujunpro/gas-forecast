<template><div class="markdown-block" v-html="sanitizeHtml(renderMarkdown(String(data.content || '')))"></div></template>
<script setup lang="ts">
import { renderMarkdown, sanitizeHtml } from '@/utils/markdown'
defineProps<{ data: Record<string, any> }>()
</script>
<style scoped>
.markdown-block {
  color: #334155;
  font-size: 14px;
  font-weight: 400;
  overflow-wrap: anywhere;
  line-height: 1.75;
}
.markdown-block :deep(p) { margin: 0 0 12px; }
.markdown-block :deep(p:last-child) { margin-bottom: 0; }
.markdown-block :deep(h1),
.markdown-block :deep(h2),
.markdown-block :deep(h3),
.markdown-block :deep(h4) { margin: 12px 0 6px; font-size: 14px; font-weight: 600; line-height: 1.75; }
.markdown-block :deep(strong) { font-weight: 600; }
.markdown-block :deep(ul),
.markdown-block :deep(ol) { margin: 8px 0 12px; padding-left: 22px; }
.markdown-block :deep(li) { margin: 0 0 8px; padding-left: 2px; }
.markdown-block :deep(li:last-child) { margin-bottom: 0; }
.markdown-block :deep(li > p) { margin: 0 0 6px; }
.markdown-block :deep(pre) { overflow-x: auto; padding: 12px; border-radius: 6px; background: #f1f5f9; }
</style>
