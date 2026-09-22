<template>
  <section class="forecast-data-page">
    <PageBreadcrumb />
    <AppTablePanel>
      <template #filters>
        <el-input v-model="keyword" clearable placeholder="预测批次号" @keyup.enter="loadData" />
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      </template>
      <AppTable v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="forecastBatchNo" label="预测批次号" min-width="220" fixed />
        <el-table-column prop="forecastDate" label="预测日期" min-width="140" />
        <el-table-column prop="forecastValue" label="预测值" min-width="150" align="right" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      </AppTable>
      <template #footer>
        <AppPagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          @current-change="loadData"
          @size-change="loadData"
        />
      </template>
    </AppTablePanel>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { listPage } from '@/api/management'
import AppPagination from '@/components/AppPagination.vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'

const route = useRoute()
const rows = ref<Record<string, any>[]>([])
const loading = ref(false)
const keyword = ref(String(route.query.batchNo || ''))
const page = ref(1)
const size = ref(20)
const total = ref(0)

const loadData = async () => {
  loading.value = true
  try {
    const result = await listPage('/model-forecast-result', {
      page: page.value,
      size: size.value,
      forecastBatchNo: keyword.value
    })
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

watch(
  () => route.fullPath,
  () => {
    keyword.value = String(route.query.batchNo || '')
    page.value = 1
    void loadData()
  }
)
onMounted(loadData)
</script>

<style scoped>
.forecast-data-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
