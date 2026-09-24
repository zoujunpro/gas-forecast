<template>
  <section class="sales-page">
    <PageBreadcrumb />

    <AppTablePanel>
      <template #filters>
        <el-input
          v-model="keyword"
          class="search-input"
          clearable
          :prefix-icon="Search"
          placeholder="搜索区域、行业、客户、来源文件"
          @clear="searchData"
          @keyup.enter="searchData"
        />
        <el-date-picker
          v-model="dateRange"
          class="date-range"
          :type="config.pickerType"
          range-separator="至"
          :start-placeholder="config.startPlaceholder"
          :end-placeholder="config.endPlaceholder"
          :value-format="config.valueFormat"
          clearable
          @change="searchData"
        />
        <el-select
          v-model="customerCode"
          class="scope-select customer-select"
          clearable
          filterable
          placeholder="客户"
          @change="handleCustomerChange"
        >
          <el-option
            v-for="option in availableCustomerOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="regionCode"
          class="scope-select"
          clearable
          filterable
          placeholder="地区"
          :disabled="Boolean(customerCode)"
          @change="searchData"
        >
          <el-option v-for="option in regionOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select
          v-model="industryCode"
          class="scope-select"
          clearable
          filterable
          placeholder="行业"
          :disabled="Boolean(customerCode)"
          @change="searchData"
        >
          <el-option
            v-for="option in industryOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" :icon="Search" @click="searchData">查询</el-button>
        <el-button type="info" plain @click="resetSearch">重置</el-button>
      </template>

      <AppTable v-loading="loading" :data="records" stripe border>
        <el-table-column
          type="index"
          :index="rowIndex"
          label="序号"
          width="72"
          fixed
          class-name="id-column"
          label-class-name="id-column"
        />
        <el-table-column prop="statDate" :label="config.dateLabel" width="130" />
        <el-table-column prop="regionName" label="区域名称" min-width="130" />
        <el-table-column prop="industryName" label="行业名称" min-width="130" />
        <el-table-column prop="customerName" label="客户名称" min-width="180" />
        <el-table-column prop="gasSales" label="销量" min-width="120" align="right" />
        <el-table-column prop="fileId" label="来源文件" min-width="150" />
        <el-table-column prop="createdAt" label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatValue(row.createdAt) }}</template>
        </el-table-column>
      </AppTable>

      <template #footer>
        <AppPagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </template>
    </AppTablePanel>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import AppPagination from '@/components/AppPagination.vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'
import { listPage } from '@/api/management'
import { displayValue } from '@/utils/http'
import { usePageQuery } from '@/composables/usePageQuery'

interface PageConfig {
  title: string
  endpoint: string
  dateLabel: string
  pickerType: 'daterange' | 'monthrange'
  valueFormat: string
  startPlaceholder: string
  endPlaceholder: string
}

const route = useRoute()
const dateRange = ref<[string, string] | null>(null)
const customerCode = ref('')
const regionCode = ref('')
const industryCode = ref('')
const customerOptions = ref<ScopeOption[]>([])
const regionOptions = ref<ScopeOption[]>([])
const industryOptions = ref<ScopeOption[]>([])

interface ScopeOption {
  label: string
  value: string
  regionCode?: string
  industryCode?: string
}

const pageConfigs: Record<string, PageConfig> = {
  '/data/daily-sales': {
    title: '日销量标准数据',
    endpoint: '/data-daily-sales',
    dateLabel: '统计日期',
    pickerType: 'daterange',
    valueFormat: 'YYYY-MM-DD',
    startPlaceholder: '开始日期',
    endPlaceholder: '结束日期'
  },
  '/data/monthly-sales': {
    title: '月销量标准数据',
    endpoint: '/data-monthly-sales',
    dateLabel: '统计月份',
    pickerType: 'monthrange',
    valueFormat: 'YYYY-MM',
    startPlaceholder: '开始月份',
    endPlaceholder: '结束月份'
  }
}

const config = computed(() => pageConfigs[route.path] || pageConfigs['/data/daily-sales'])
const availableCustomerOptions = computed(() =>
  customerOptions.value.filter(
    (option) =>
      (!regionCode.value || option.regionCode === regionCode.value) &&
      (!industryCode.value || option.industryCode === industryCode.value)
  )
)

const {
  loading,
  keyword,
  page,
  size,
  total,
  records,
  loadData,
  searchData,
  resetSearch: resetKeywordSearch,
  handleSizeChange,
  rowIndex
} = usePageQuery<Record<string, any>>({
  errorMessage: '列表加载失败',
  fetcher: async ({ page, size, keyword, signal }) => {
    return listPage(
      config.value.endpoint,
      {
        page,
        size,
        keyword: keyword || undefined,
        startDate: dateRange.value?.[0],
        endDate: dateRange.value?.[1],
        customerCode: customerCode.value || undefined,
        regionCode: regionCode.value || undefined,
        industryCode: industryCode.value || undefined
      },
      signal
    )
  }
})

const resetSearch = () => {
  dateRange.value = null
  customerCode.value = ''
  regionCode.value = ''
  industryCode.value = ''
  resetKeywordSearch()
}

const handleCustomerChange = () => {
  if (customerCode.value) {
    const customer = customerOptions.value.find((option) => option.value === customerCode.value)
    regionCode.value = customer?.regionCode || ''
    industryCode.value = customer?.industryCode || ''
  }
  searchData()
}

const loadScopeOptions = async () => {
  try {
    const [customers, regions, industries] = await Promise.all([
      listPage('/base-customer', { page: 1, size: 1000 }),
      listPage('/base-region', { page: 1, size: 1000 }),
      listPage('/base-industry', { page: 1, size: 1000 })
    ])
    customerOptions.value = customers.records.map((item) => ({
      label: `${item.customerName || '-'} (${item.customerCode || '-'})`,
      value: item.customerCode,
      regionCode: item.regionCode,
      industryCode: item.industryCode
    }))
    regionOptions.value = regions.records.map((item) => ({
      label: `${item.regionName || '-'} (${item.regionCode || '-'})`,
      value: item.regionCode
    }))
    industryOptions.value = industries.records.map((item) => ({
      label: `${item.industryName || '-'} (${item.industryCode || '-'})`,
      value: item.industryCode
    }))
  } catch {
    ElMessage.error('客户、地区或行业选项加载失败')
  }
}

const formatValue = displayValue

watch(
  () => route.path,
  () => {
    keyword.value = ''
    dateRange.value = null
    customerCode.value = ''
    regionCode.value = ''
    industryCode.value = ''
    page.value = 1
    loadData()
  }
)

onMounted(() => {
  void loadScopeOptions()
  void loadData()
})
</script>

<style scoped>
.sales-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.date-range {
  width: 260px;
}

.search-input {
  width: 280px;
}

.scope-select {
  width: 160px;
}

.customer-select {
  width: 180px;
}

@media (max-width: 900px) {
  .date-range,
  .search-input,
  .scope-select,
  .customer-select {
    width: 100%;
  }
}
</style>
