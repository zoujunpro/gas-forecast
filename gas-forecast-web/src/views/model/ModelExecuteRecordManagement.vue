<template>
  <section class="record-page">
    <PageBreadcrumb />
    <AppTablePanel>
      <template #filters>
        <el-input v-model="filters.forecastNo" clearable placeholder="预测编号" @keyup.enter="loadData" />
        <el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="item in statuses" :key="item" :label="item" :value="item" /></el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
      </template>
      <AppTable v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="forecastNo" label="预测编号" min-width="230" />
        <el-table-column prop="agentCode" label="智能体" width="130" />
        <el-table-column label="预测对象" min-width="180"><template #default="{ row }">{{ row.customerName || row.customerCode || row.industryName || row.industryCode || row.regionName || row.regionCode || '-' }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="105"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="bestModel" label="最佳模型" min-width="150" show-overflow-tooltip />
        <el-table-column label="MAPE" width="100"><template #default="{ row }">{{ percent(row.mape) }}</template></el-table-column>
        <el-table-column label="预测范围" min-width="160"><template #default="{ row }">{{ row.forecastStartDate || '-' }} · {{ row.forecastHorizon || '-' }} {{ row.forecastUnit || '' }}</template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="175" />
        <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template></el-table-column>
      </AppTable>
      <template #footer><AppPagination v-model:current-page="page" v-model:page-size="size" :total="total" @current-change="loadData" @size-change="loadData" /></template>
    </AppTablePanel>
    <el-drawer v-model="detailVisible" size="min(900px, 92vw)" destroy-on-close><template #header><strong>预测记录详情 · {{ detail?.forecastNo }}</strong></template>
      <el-descriptions v-if="detail" :column="2" border><el-descriptions-item label="状态"><el-tag :type="statusType(detail.status)">{{ detail.status }}</el-tag></el-descriptions-item><el-descriptions-item label="最佳模型">{{ detail.bestModel || '-' }}</el-descriptions-item><el-descriptions-item label="MAPE">{{ percent(detail.mape) }}</el-descriptions-item><el-descriptions-item label="执行耗时">{{ detail.executeDurationSeconds || '-' }} 秒</el-descriptions-item></el-descriptions>
      <el-tabs v-if="detail" class="record-tabs"><el-tab-pane label="训练结果"><pre>{{ pretty(detail.trainingResultJson) }}</pre></el-tab-pane><el-tab-pane label="回测结果"><pre>{{ pretty(detail.backtestResultJson) }}</pre></el-tab-pane><el-tab-pane label="预测结果"><pre>{{ pretty(detail.forecastResultJson) }}</pre></el-tab-pane><el-tab-pane label="错误信息"><pre>{{ detail.errorMessage || '无' }}</pre></el-tab-pane></el-tabs>
    </el-drawer>
  </section>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getJson, listPage } from '@/api/management'
defineOptions({ name: 'ModelExecuteRecordManagement' })
const page = ref(1), size = ref(20), total = ref(0), loading = ref(false), rows = ref<any[]>([]), detail = ref<any>(), detailVisible = ref(false)
const filters = reactive({ forecastNo: '', status: '' }); const statuses = ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED']
const loadData = async () => { loading.value = true; try { const data:any = await listPage('/model-execute-record', { page: page.value, size: size.value, ...filters }); rows.value = data.records || []; total.value = data.total || 0 } finally { loading.value = false } }
const openDetail = async (row:any) => { const response:any = await getJson(`/model-execute-record/${row.forecastNo}`); detail.value = response.data || response; detailVisible.value = true }
const pretty = (value?:string) => { try { return value ? JSON.stringify(JSON.parse(value), null, 2) : '暂无数据' } catch { return value || '暂无数据' } }
const percent = (value?:number) => value == null ? '-' : `${(value * 100).toFixed(2)}%`
const statusType = (value:string) => value === 'SUCCESS' ? 'success' : value === 'FAILED' ? 'danger' : value === 'RUNNING' ? 'warning' : 'info'
onMounted(loadData)
</script>
<style scoped>.record-tabs{margin-top:20px}pre{white-space:pre-wrap;word-break:break-word;background:#f7f8fa;padding:16px;border-radius:8px;max-height:520px;overflow:auto}</style>
