<template>
  <section v-loading="loading" class="content-table">
    <h4>{{ data.title || '查询结果' }}</h4>
    <p>共 {{ total }} 条，当前加载 {{ rows.length }} 条</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="visibleRows" max-height="420" stripe>
      <el-table-column v-for="column in columns" :key="column.field" :prop="column.field" :label="column.title" min-width="140" show-overflow-tooltip />
    </el-table>
    <el-pagination v-if="total > size && (remote || rows.length >= total)" :current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="changePage" />
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { presentationColumns } from './presentationFields'
import { postJson } from '@/api/management'
const props = defineProps<{ data: Record<string, any> }>()
const rows = ref<Record<string, any>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const error = ref('')
let requestId = 0
watch(() => props.data, (data) => {
  requestId++
  rows.value = data.rows || data.data?.records || []
  total.value = Number(data.total ?? data.data?.total ?? rows.value.length)
  page.value = Number(data.page || 1)
  size.value = Number(data.pageSize || 20)
  error.value = ''
  loading.value = false
}, { immediate: true })
const remote = computed(() => props.data.pagination?.resource === 'customers')
const visibleRows = computed(() => remote.value ? rows.value.slice(0, size.value) : rows.value.slice((page.value - 1) * size.value, page.value * size.value))
const columns = computed(() => presentationColumns(rows.value, props.data.columns))
async function changePage(next: number) {
  if (!remote.value) { page.value = next; return }
  const id = ++requestId
  loading.value = true
  error.value = ''
  try {
    const response = await postJson<any>('/base-customer/agentPage', { ...props.data.pagination.filters, page: next, size: size.value })
    if (id !== requestId) return
    rows.value = response.data.records
    total.value = response.data.total
    page.value = next
  } catch (cause: any) {
    if (id === requestId) error.value = cause.message || '加载失败，请重试'
  } finally { if (id === requestId) loading.value = false }
}
</script>
<style scoped>
.content-table { margin: 12px 0; max-width: 100%; overflow: hidden; }
p { font-size: 12px; color: #666; }
</style>
