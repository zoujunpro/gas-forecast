<template>
  <section class="forecast-page">
    <PageBreadcrumb />
    <AppTablePanel>
      <template #filters>
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索预测名称"
          :prefix-icon="Search"
          @keyup.enter="loadData"
        />
        <el-select v-model="agentCode" clearable placeholder="全部智能体">
          <el-option v-for="item in agents" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      </template>
      <template #actions><el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button></template>
      <AppTable v-loading="loading" :data="rows" border stripe @row-click="openInstances">
        <el-table-column prop="forecastName" label="预测名称" min-width="190" fixed />
        <el-table-column prop="agentCode" label="智能体" min-width="140"
          ><template #default="{ row }">{{ agentLabel(row.agentCode) }}</template></el-table-column
        >
        <el-table-column prop="regionName" label="区域" min-width="110" />
        <el-table-column prop="industryName" label="行业" min-width="110" />
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column prop="forecastStartDate" label="预测开始日期" width="125" />
        <el-table-column prop="forecastHorizon" label="预测步长" width="100" align="right" />
        <el-table-column prop="forecastFrequency" label="时间颗粒度" width="110" />
        <el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="createdByName" label="创建人" width="110" />
        <el-table-column label="创建日期" width="120"
          ><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column
        >
        <el-table-column label="操作" width="330" fixed="right"
          ><template #default="{ row }"
            ><span @click.stop
              ><el-button link type="success" :loading="runningCode === row.id" @click="openPredict(row)"
                >发起预测</el-button
              ><el-button link type="primary" @click="openInstances(row)">查看预测实例</el-button
              ><el-button link type="primary" @click="openEdit(row)">编辑</el-button
              ><el-button link type="danger" @click="remove(row)">删除</el-button></span
            ></template
          ></el-table-column
        >
      </AppTable>
      <template #footer
        ><AppPagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          @current-change="loadData"
          @size-change="loadData"
      /></template>
    </AppTablePanel>

    <el-dialog
      v-model="drawerVisible"
      width="min(860px, calc(100vw - 32px))"
      class="forecast-config-dialog"
      destroy-on-close
      align-center
    >
      <template #header>
        <div class="forecast-editor-head">
          <span class="forecast-editor-icon"
            ><el-icon><TrendCharts /></el-icon
          ></span>
          <div>
            <h2>{{ form.id ? '编辑预测配置' : '新增预测配置' }}</h2>
            <p>绑定已完成训练的模型，并设置未来预测的起点和周期</p>
          </div>
        </div>
      </template>

      <el-form ref="formRef" class="forecast-editor-form" label-position="top" :model="form" :rules="rules">
        <section class="forecast-editor-section">
          <header class="forecast-section-head">
            <span>01</span>
            <div><strong>基础信息</strong><small>设置配置名称并选择预测使用的训练模型</small></div>
          </header>
          <div class="form-grid">
            <el-form-item label="预测名称" prop="forecastName">
              <el-input v-model="form.forecastName" maxlength="128" show-word-limit placeholder="请输入预测名称" />
            </el-form-item>
            <el-form-item label="模型训练配置" prop="trainConfigCode">
              <el-select
                v-model="form.trainConfigCode"
                filterable
                placeholder="请选择已启用的训练配置"
                @change="handleTrainConfigChange"
              >
                <el-option
                  v-for="item in trainConfigOptions"
                  :key="item.trainCode"
                  :label="`${item.trainName}（${item.trainCode}）`"
                  :value="item.trainCode"
                />
              </el-select>
            </el-form-item>
          </div>

          <div v-if="selectedTrainConfig" class="train-config-card">
            <span class="train-config-icon"
              ><el-icon><Connection /></el-icon
            ></span>
            <div class="train-config-main">
              <strong>{{ selectedTrainConfig.modelName || selectedTrainConfig.trainName }}</strong>
              <span>{{ selectedTrainConfig.trainCode }} · {{ agentLabel(selectedTrainConfig.agentCode) }}</span>
            </div>
            <div class="train-scope-tags">
              <el-tag effect="plain">{{ selectedTrainConfig.regionName || '全部区域' }}</el-tag>
              <el-tag v-if="selectedTrainConfig.industryName" type="success" effect="plain">
                {{ selectedTrainConfig.industryName }}
              </el-tag>
              <el-tag v-if="selectedTrainConfig.customerName" type="warning" effect="plain">
                {{ selectedTrainConfig.customerName }}
              </el-tag>
            </div>
          </div>
        </section>

        <section class="forecast-editor-section">
          <header class="forecast-section-head">
            <span>02</span>
            <div><strong>预测策略</strong><small>定义未来预测从哪一天开始，以及需要预测多长时间</small></div>
          </header>
          <div class="form-grid strategy-grid">
            <el-form-item label="预测开始日期" prop="forecastStartDate">
              <el-date-picker
                v-model="form.forecastStartDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择预测开始日期"
              />
            </el-form-item>
            <el-form-item label="时间颗粒度" prop="forecastFrequency">
              <el-select v-model="form.forecastFrequency">
                <el-option label="按日预测" value="DAILY" />
                <el-option label="按旬预测" value="TENDAY" />
                <el-option label="按月预测" value="MONTHLY" />
              </el-select>
            </el-form-item>
            <el-form-item label="预测周期" prop="forecastHorizon">
              <el-input-number v-model="form.forecastHorizon" :min="1" :max="366" controls-position="right" />
              <span class="field-help">共预测 {{ form.forecastHorizon }} {{ forecastFrequencyUnit }}</span>
            </el-form-item>
            <el-form-item label="启用状态">
              <div class="status-switch-row">
                <el-switch v-model="form.enabled" />
                <span>{{ form.enabled ? '启用后可直接发起预测' : '当前配置暂不启用' }}</span>
              </div>
            </el-form-item>
          </div>
        </section>

        <section class="forecast-editor-section compact-section">
          <header class="forecast-section-head">
            <span>03</span>
            <div><strong>补充说明</strong><small>记录预测用途、口径或其他注意事项</small></div>
          </header>
          <el-form-item class="remark-item" label="备注">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请输入预测配置的补充说明（选填）"
            />
          </el-form-item>
        </section>
      </el-form>
      <template #footer
        ><el-button @click="drawerVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
      >
    </el-dialog>

    <el-dialog
      v-model="instancesVisible"
      fullscreen
      class="instances-dialog"
      destroy-on-close
      @opened="renderResultChart"
    >
      <template #header>
        <div class="forecast-result-heading">
          <h2>预测结果</h2>
          <p>查看预测批次、特征数据及预测结果趋势</p>
        </div>
      </template>
      <div class="instances-workbench">
        <aside class="instances-sidebar">
          <div class="result-sidebar-head">
            <strong
              ><el-icon><Tickets /></el-icon>预测批次</strong
            >
            <span>{{ filteredRecordRows.length }} / {{ recordRows.length }} 条</span>
          </div>
          <div class="result-sidebar-search">
            <el-input v-model="recordKeyword" clearable :prefix-icon="Search" placeholder="请输入批次号/模型名称" />
            <el-button :icon="Filter" />
          </div>
          <div class="result-sidebar-tabs">
            <button
              v-for="tab in recordStatusTabs"
              :key="tab.value"
              type="button"
              :class="{ active: recordStatusFilter === tab.value }"
              @click="handleRecordFilter(tab.value)"
            >
              {{ tab.label }}
            </button>
          </div>
          <div class="instance-list" v-loading="recordsLoading">
            <button
              v-for="row in pagedRecordRows"
              :key="row.id"
              type="button"
              class="instance-card"
              :class="{ active: activeRecord?.id === row.id }"
              @click="selectRecord(row)"
            >
              <div>
                <strong>{{ row.forecastName || selectedConfig?.forecastName || '预测任务' }}</strong
                ><el-tag
                  size="small"
                  :type="recordStatus(row.status).type"
                  :class="['forecast-status-tag', `status-${Number(row.status)}`]"
                  effect="light"
                  >{{ recordStatus(row.status).label }}</el-tag
                >
              </div>
              <p>{{ row.forecastBatchNo }}</p>
              <small>{{ formatDateTime(row.forecastEndTime || row.updatedAt || row.createdAt) }}</small>
              <el-button
                v-if="Number(row.status) === 3"
                link
                type="warning"
                :loading="runningCode === Number(row.forecastId)"
                @click.stop="retryRecord(row)"
                >重新预测</el-button
              >
            </button>
            <el-empty v-if="!recordsLoading && !pagedRecordRows.length" description="暂无匹配批次" />
          </div>
          <AppPagination
            v-if="filteredRecordRows.length"
            v-model:current-page="recordPage"
            v-model:page-size="recordSize"
            class="result-sidebar-pagination"
            :page-sizes="[5, 10, 20]"
            :total="filteredRecordRows.length"
            @size-change="recordPage = 1"
          />
        </aside>
        <main v-if="activeRecord" class="instance-detail">
          <section class="panel-title">
            <div>
              <div class="detail-title-line">
                <h3>{{ selectedConfig?.forecastName || '预测详情' }}</h3>
                <el-tag
                  :type="recordStatus(activeRecord.status).type"
                  :class="['forecast-status-tag', `status-${Number(activeRecord.status)}`]"
                  effect="light"
                  >{{ recordStatus(activeRecord.status).label }}</el-tag
                >
              </div>
              <p>
                <span>{{ activeRecord.forecastBatchNo }}</span>
                <span>创建时间：{{ formatDateTime(activeRecord.createdAt) }}</span>
                <span>完成时间：{{ formatDateTime(activeRecord.forecastEndTime) }}</span>
              </p>
            </div>
          </section>
          <section class="forecast-summary-grid">
            <article v-for="group in forecastSummaryGroups" :key="group.title" class="forecast-summary-card">
              <h4>
                <span
                  ><el-icon><component :is="group.icon" /></el-icon></span
                >{{ group.title }}
              </h4>
              <div class="forecast-summary-list">
                <div v-for="item in group.items" :key="item.label" class="forecast-summary-item">
                  <span>{{ item.label }}</span>
                  <el-tag
                    v-if="item.status"
                    :type="recordStatus(activeRecord.status).type"
                    :class="['forecast-status-tag', `status-${Number(activeRecord.status)}`]"
                    size="small"
                    effect="light"
                    >{{ item.value }}</el-tag
                  >
                  <strong v-else :title="String(item.value)">{{ item.value }}</strong>
                </div>
              </div>
            </article>
          </section>
          <section class="detail-card forecast-data-card">
            <el-tabs v-model="detailTab" class="forecast-data-tabs" @tab-change="handleDetailTabChange">
              <el-tab-pane label="预测结果" name="result">
                <template v-if="Number(activeRecord.status) === 2">
                  <div class="result-heading">
                    <h4 class="section-title">预测结果趋势</h4>
                    <el-radio-group v-model="resultView" size="small" @change="handleResultViewChange"
                      ><el-radio-button value="chart">图表</el-radio-button
                      ><el-radio-button value="table">列表</el-radio-button></el-radio-group
                    >
                  </div>
                  <div
                    v-show="resultView === 'chart'"
                    v-loading="resultLoading"
                    ref="resultChartRef"
                    class="result-chart"
                  ></div>
                  <template v-if="resultView === 'table'">
                    <AppTable v-loading="resultLoading" :data="resultRows" border stripe
                      ><el-table-column prop="forecastDate" label="预测日期" min-width="160" /><el-table-column
                        prop="forecastValue"
                        label="预测值"
                        min-width="180"
                        align="right"
                      /><el-table-column label="创建日期" width="130"
                        ><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column
                      ></AppTable
                    >
                    <AppPagination
                      v-model:current-page="resultPage"
                      v-model:page-size="resultSize"
                      :total="resultTotal"
                      @current-change="loadResultRows"
                      @size-change="loadResultRows"
                    />
                  </template>
                </template>
                <el-empty
                  v-else
                  :image-size="72"
                  :description="
                    Number(activeRecord.status) === 3 ? '预测失败，暂无预测结果' : '预测任务执行中，暂无预测结果'
                  "
                />
              </el-tab-pane>
              <el-tab-pane label="预测特征" name="features">
                <AppTable v-if="featureSnapshotRows.length" :data="featureSnapshotRows" border stripe max-height="420">
                  <el-table-column prop="date" label="预测日期" min-width="130" fixed />
                  <el-table-column
                    v-for="column in featureSnapshotColumns"
                    :key="column"
                    :prop="column"
                    :label="column"
                    min-width="150"
                    show-overflow-tooltip
                  />
                </AppTable>
                <el-empty v-else :image-size="54" description="暂无预测特征快照" />
              </el-tab-pane>
            </el-tabs>
          </section>
        </main>
        <el-empty v-else class="instance-detail" description="请选择左侧预测实例" />
      </div>
    </el-dialog>

    <el-dialog v-model="predictVisible" title="填写预测特征数据" width="900px" destroy-on-close align-center>
      <el-alert
        title="请输入 JSON 数组，每条数据必须包含 date 字段；确认后将作为预测接口的 dataset 参数。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-input
        v-model="featureDataJson"
        class="feature-json-input"
        type="textarea"
        :rows="18"
        resize="vertical"
        spellcheck="false"
        placeholder='[{"date":"2026-05-26","未来动态特征":100}]'
      />
      <template #footer>
        <el-button @click="predictVisible = false">取消</el-button>
        <el-button type="primary" :loading="runningCode !== null" @click="confirmPredict">确认预测</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Calendar, Connection, Filter, Plus, Search, Tickets, TrendCharts } from '@element-plus/icons-vue'
import { deleteRow, listPage, postJson } from '@/api/management'
import AppPagination from '@/components/AppPagination.vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'

const agents = [
  { label: '冬季保供', value: 'winter-supply' },
  { label: '月度销量', value: 'monthly-sales' },
  { label: '短期客户', value: 'short-term' }
]
const rows = ref<Record<string, any>[]>([]),
  keyword = ref(''),
  agentCode = ref(''),
  loading = ref(false),
  drawerVisible = ref(false),
  runningCode = ref<number | null>(null)
const saving = ref(false),
  formRef = ref<FormInstance>()
const page = ref(1),
  size = ref(10),
  total = ref(0)
const recordsLoading = ref(false),
  recordRows = ref<Record<string, any>[]>([]),
  selectedConfig = ref<Record<string, any> | null>(null)
const instancesVisible = ref(false)
const recordStatusFilter = ref<'all' | 'running' | 'success' | 'failed'>('all')
const recordKeyword = ref('')
const recordStatusTabs = [
  { label: '全部', value: 'all' },
  { label: '运行中', value: 'running' },
  { label: '成功', value: 'success' },
  { label: '失败', value: 'failed' }
] as const
const predictVisible = ref(false),
  featureDataJson = ref('[\n  {\n    "date": ""\n  }\n]'),
  pendingForecast = ref<Record<string, any> | null>(null)
const activeRecord = ref<Record<string, any> | null>(null)
const resultLoading = ref(false),
  resultRows = ref<Record<string, any>[]>([]),
  resultPage = ref(1),
  resultSize = ref(20),
  resultTotal = ref(0)
const resultView = ref<'chart' | 'table'>('chart'),
  resultChartRef = ref<HTMLElement>(),
  chartRows = ref<Record<string, any>[]>([]),
  historyRows = ref<Record<string, any>[]>([]),
  featureSnapshotRows = ref<Record<string, any>[]>([])
const detailTab = ref<'result' | 'features'>('result')
let resultChart: echarts.ECharts | null = null
const recordPage = ref(1),
  recordSize = ref(5),
  currentForecastId = ref<number | null>(null)
const predictionInfo = computed(() => {
  const frequencyCode = String(
    activeRecord.value?.forecastFrequency || selectedConfig.value?.forecastFrequency || 'DAILY'
  ).toUpperCase()
  const frequencyMap: Record<string, { label: string; unit: string }> = {
    DAILY: { label: '日', unit: '天' },
    DAY: { label: '日', unit: '天' },
    TENDAY: { label: '旬', unit: '旬' },
    MONTHLY: { label: '月', unit: '个月' },
    MONTH: { label: '月', unit: '个月' }
  }
  const frequency = frequencyMap[frequencyCode] || { label: frequencyCode || '-', unit: '期' }
  const futureDates = chartRows.value
    .map((item) => String(item.forecastDate || ''))
    .filter(Boolean)
    .sort()
  const historicalActualDates = historyRows.value
    .map((item) => String(item.date || ''))
    .filter(Boolean)
    .sort()
  const historicalPredictionDates = historyRows.value
    .filter((item) => item.predictedValue !== null && item.predictedValue !== undefined)
    .map((item) => String(item.date || ''))
    .filter(Boolean)
    .sort()
  const plannedCount = Number(activeRecord.value?.forecastHorizon || selectedConfig.value?.forecastHorizon || 0)
  return {
    frequency: frequency.label,
    unit: frequency.unit,
    futureStart:
      futureDates[0] || activeRecord.value?.forecastStartDate || selectedConfig.value?.forecastStartDate || '-',
    futureEnd: futureDates[futureDates.length - 1] || '-',
    futureCount: futureDates.length || plannedCount || '-',
    historyPredictionStart: historicalPredictionDates[0] || '-',
    historyPredictionEnd: historicalPredictionDates[historicalPredictionDates.length - 1] || '-',
    historyPredictionCount: historicalPredictionDates.length || '-',
    historyActualRange: historicalActualDates.length
      ? `${historicalActualDates[0]} 至 ${historicalActualDates[historicalActualDates.length - 1]}`
      : '-'
  }
})
const forecastOverviewRows = computed(() => {
  const record = activeRecord.value || {}
  const config = selectedConfig.value || {}
  return [
    { label: '预测批次号', value: record.forecastBatchNo || '-' },
    { label: '预测状态', value: recordStatus(record.status).label, status: true },
    { label: '预测名称', value: config.forecastName || '-' },
    {
      label: '预测模型',
      value: record.modelName || config.modelName || agentLabel(record.agentCode || config.agentCode)
    },
    { label: '未来预测开始', value: predictionInfo.value.futureStart },
    { label: '未来预测结束', value: predictionInfo.value.futureEnd },
    { label: '预测步长', value: `${predictionInfo.value.futureCount} ${predictionInfo.value.unit}` },
    { label: '时间颗粒度', value: predictionInfo.value.frequency },
    { label: '历史预测开始', value: predictionInfo.value.historyPredictionStart },
    { label: '历史预测结束', value: predictionInfo.value.historyPredictionEnd },
    {
      label: '历史预测数量',
      value: `${predictionInfo.value.historyPredictionCount} ${predictionInfo.value.unit}`
    },
    { label: '历史实际区间', value: predictionInfo.value.historyActualRange },
    { label: '训练配置', value: record.trainConfigCode || config.trainConfigCode || '-' },
    { label: '智能体', value: agentLabel(record.agentCode || config.agentCode) },
    { label: '预测创建时间', value: formatDateTime(record.createdAt) },
    { label: '完成时间', value: formatDateTime(record.forecastEndTime) },
    { label: '发起人', value: record.createdByName || '-' },
    { label: '区域', value: record.regionName || '全部' },
    { label: '行业', value: record.industryName || '全部' },
    { label: '客户', value: record.customerName || '全部' },
    { label: '备注', value: record.remark || '-' }
  ]
})
const forecastSummaryGroups = computed(() => {
  const rows = forecastOverviewRows.value
  const pick = (...labels: string[]) => rows.filter((item) => labels.includes(item.label))
  return [
    {
      title: '任务信息',
      icon: Tickets,
      items: pick('预测批次号', '预测名称', '训练配置', '预测模型', '智能体', '预测状态')
    },
    {
      title: '预测范围',
      icon: Calendar,
      items: pick('未来预测开始', '未来预测结束', '预测步长', '时间颗粒度', '历史预测数量', '历史实际区间')
    },
    {
      title: '业务范围',
      icon: Connection,
      items: pick('区域', '行业', '客户', '发起人', '备注')
    }
  ]
})
const featureSnapshotColumns = computed(() => {
  const columns = new Set<string>()
  featureSnapshotRows.value.forEach((row) => {
    Object.keys(row).forEach((key) => {
      if (key !== 'date') columns.add(key)
    })
  })
  return [...columns]
})
const filteredRecordRows = computed(() => {
  const keywordValue = recordKeyword.value.trim().toLowerCase()
  const statusMap = { running: 1, success: 2, failed: 3 } as const
  return recordRows.value.filter((row) => {
    if (recordStatusFilter.value !== 'all' && Number(row.status) !== statusMap[recordStatusFilter.value]) return false
    if (!keywordValue) return true
    return [row.forecastBatchNo, row.modelName, row.agentName, row.forecastName]
      .filter((value) => value !== undefined && value !== null)
      .join(' ')
      .toLowerCase()
      .includes(keywordValue)
  })
})
const pagedRecordRows = computed(() => {
  const start = (recordPage.value - 1) * recordSize.value
  return filteredRecordRows.value.slice(start, start + recordSize.value)
})
const trainConfigOptions = ref<Record<string, any>[]>([])
const emptyForm = () => ({
  id: undefined as number | undefined,
  forecastName: '',
  agentCode: 'winter-supply',
  scopeType: 'ALL',
  regionCode: '',
  regionName: '',
  industryCode: '',
  industryName: '',
  customerCode: '',
  customerName: '',
  trainConfigCode: '',
  forecastStartDate: '',
  forecastHorizon: 15,
  forecastFrequency: 'TENDAY',
  autoForecast: false,
  enabled: true,
  remark: ''
})
const form = reactive(emptyForm())
const rules: FormRules = {
  forecastName: [{ required: true, message: '请输入预测名称', trigger: 'blur' }],
  trainConfigCode: [{ required: true, message: '请选择模型训练配置', trigger: 'change' }],
  forecastStartDate: [{ required: true, message: '请选择预测开始日期', trigger: 'change' }],
  forecastHorizon: [{ required: true, message: '请输入预测步长', trigger: 'change' }],
  forecastFrequency: [{ required: true, message: '请选择时间颗粒度', trigger: 'change' }]
}
const selectedTrainConfig = computed(() =>
  trainConfigOptions.value.find((item) => item.trainCode === form.trainConfigCode)
)
const forecastFrequencyUnit = computed(
  () =>
    ({ DAILY: '天', TENDAY: '旬', MONTHLY: '个月' })[form.forecastFrequency as 'DAILY' | 'TENDAY' | 'MONTHLY'] || '期'
)
const agentLabel = (code: string) => agents.find((item) => item.value === code)?.label || code
const formatDate = (value: unknown) => (value ? String(value).slice(0, 10) : '-')
const formatDateTime = (value: unknown) => {
  if (!value) return '-'
  const text = String(value).trim().replace('T', ' ')
  const matched = text.match(/^(\d{4}-\d{2}-\d{2})(?:\s+(\d{2}:\d{2}:\d{2}))?/)
  if (!matched) return text
  return matched[2] ? `${matched[1]} ${matched[2]}` : matched[1]
}
const loadData = async () => {
  loading.value = true
  try {
    const data = await listPage('/model-forecast-config', {
      page: page.value,
      size: size.value,
      keyword: keyword.value,
      agentCode: agentCode.value
    })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const loadTrainConfigs = async () => {
  const configs = await listPage('/model-train-config', { page: 1, size: 1000 })
  trainConfigOptions.value = configs.records.filter((item: any) => Number(item.enabled) === 1)
}
const handleTrainConfigChange = () => {
  const item = selectedTrainConfig.value
  if (!item) return
  form.agentCode = item.agentCode
  form.scopeType = item.customerCode ? 'CUSTOMER' : item.industryCode ? 'INDUSTRY' : item.regionCode ? 'REGION' : 'ALL'
  form.regionCode = item.regionCode || ''
  form.regionName = item.regionName || ''
  form.industryCode = item.industryCode || ''
  form.industryName = item.industryName || ''
  form.customerCode = item.customerCode || ''
  form.customerName = item.customerName || ''
  const defaults: Record<string, [string, number]> = {
    'winter-supply': ['TENDAY', 15],
    'monthly-sales': ['MONTHLY', 12],
    'short-term': ['DAILY', 30]
  }
  ;[form.forecastFrequency, form.forecastHorizon] = defaults[item.agentCode] || ['DAILY', 30]
}
const prepareDialogOptions = async () => {
  try {
    await loadTrainConfigs()
    if (form.trainConfigCode) handleTrainConfigChange()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '配置选项加载失败')
  } finally {
    formRef.value?.clearValidate()
  }
}
const openCreate = () => {
  Object.assign(form, emptyForm())
  drawerVisible.value = true
  void prepareDialogOptions()
}
const openEdit = (row: Record<string, any>) => {
  Object.assign(form, emptyForm(), row, {
    autoForecast: Number(row.autoForecast) === 1,
    enabled: Number(row.enabled) === 1
  })
  drawerVisible.value = true
  void prepareDialogOptions()
}
const save = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await postJson(form.id ? '/model-forecast-config/update' : '/model-forecast-config/create', {
      ...form,
      autoForecast: form.autoForecast ? 1 : 0,
      enabled: form.enabled ? 1 : 0
    })
    drawerVisible.value = false
    ElMessage.success('保存成功')
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}
const remove = async (row: Record<string, any>) => {
  await ElMessageBox.confirm(`确认删除配置“${row.forecastName}”？`, '删除确认', { type: 'warning' })
  await deleteRow('/model-forecast-config', row.id)
  ElMessage.success('删除成功')
  await loadData()
}
const recordStatus = (status: number): { label: string; type: any } =>
  ({
    1: { label: '预测中', type: 'warning' },
    2: { label: '预测成功', type: 'success' },
    3: { label: '预测失败', type: 'danger' }
  })[Number(status)] || { label: '未知', type: 'info' }
const loadRecords = async () => {
  if (!currentForecastId.value) return
  recordsLoading.value = true
  try {
    const data = await listPage('/model-forecast-record', { page: 1, size: 1000, forecastId: currentForecastId.value })
    recordRows.value = data.records
    if (recordRows.value.length) selectRecord(recordRows.value[0])
    else {
      activeRecord.value = null
      resultRows.value = []
      resultTotal.value = 0
      chartRows.value = []
      featureSnapshotRows.value = []
      renderResultChart()
    }
  } finally {
    recordsLoading.value = false
  }
}
const selectConfig = (row: Record<string, any>) => {
  selectedConfig.value = row
  currentForecastId.value = Number(row.id)
  recordPage.value = 1
  void loadRecords()
}
const openInstances = (row: Record<string, any>) => {
  recordKeyword.value = ''
  recordStatusFilter.value = 'all'
  recordPage.value = 1
  resultView.value = 'chart'
  detailTab.value = 'result'
  instancesVisible.value = true
  selectConfig(row)
}
const handleRecordFilter = (status: typeof recordStatusFilter.value) => {
  recordStatusFilter.value = status
  recordPage.value = 1
}
const loadResultRows = async () => {
  if (!activeRecord.value?.forecastBatchNo) {
    resultRows.value = []
    historyRows.value = []
    resultTotal.value = 0
    featureSnapshotRows.value = []
    return
  }
  const batchNo = String(activeRecord.value.forecastBatchNo)
  const status = Number(activeRecord.value.status)
  resultRows.value = []
  historyRows.value = []
  chartRows.value = []
  resultTotal.value = 0
  featureSnapshotRows.value = []
  resultChart?.clear()
  resultLoading.value = true
  try {
    if (status !== 2) {
      const featureResponse = await postJson('/model-forecast-record/features', { forecastBatchNo: batchNo }).catch(
        () => ({ data: [] })
      )
      if (String(activeRecord.value?.forecastBatchNo || '') !== batchNo) return
      featureSnapshotRows.value = Array.isArray(featureResponse.data) ? featureResponse.data : []
      return
    }
    const [data, historyResponse, featureResponse] = await Promise.all([
      listPage('/model-forecast-result', {
        page: resultPage.value,
        size: resultView.value === 'chart' ? 1000 : resultSize.value,
        forecastBatchNo: batchNo
      }),
      postJson('/model-forecast-result/history', { forecastBatchNo: batchNo }),
      postJson('/model-forecast-record/features', { forecastBatchNo: batchNo }).catch(() => ({ data: [] }))
    ])
    if (String(activeRecord.value?.forecastBatchNo || '') !== batchNo) return
    resultRows.value = data.records
    chartRows.value = data.records
    historyRows.value = Array.isArray(historyResponse.data) ? historyResponse.data : []
    featureSnapshotRows.value = Array.isArray(featureResponse.data) ? featureResponse.data : []
    resultTotal.value = data.total
    await nextTick()
    renderResultChart()
  } finally {
    resultLoading.value = false
  }
}
const selectRecord = (row?: Record<string, any>) => {
  if (!row) return
  activeRecord.value = row
  resultPage.value = 1
  resultRows.value = []
  historyRows.value = []
  chartRows.value = []
  resultTotal.value = 0
  featureSnapshotRows.value = []
  resultChart?.clear()
  void loadResultRows()
}
const renderResultChart = () => {
  if (!resultChartRef.value || resultView.value !== 'chart' || detailTab.value !== 'result') return
  resultChart = echarts.getInstanceByDom(resultChartRef.value) || echarts.init(resultChartRef.value)
  const history = [...historyRows.value].sort((a, b) => String(a.date).localeCompare(String(b.date)))
  const forecast = [...chartRows.value].sort((a, b) => String(a.forecastDate).localeCompare(String(b.forecastDate)))
  const dates = [...history.map((item) => String(item.date)), ...forecast.map((item) => String(item.forecastDate))]
  const historyValues = [...history.map((item) => Number(item.actualValue)), ...forecast.map(() => null)]
  const historicalForecastValues = [
    ...history.map((item) => (item.predictedValue == null ? null : Number(item.predictedValue))),
    ...forecast.map(() => null)
  ]
  const futureForecastValues = [
    ...history.map(() => null as number | null),
    ...forecast.map((item) => Number(item.forecastValue))
  ]
  const forecastBridgeValues = dates.map(() => null as number | null)
  if (history.length && forecast.length) {
    const lastHistoricalPrediction = historicalForecastValues[history.length - 1]
    if (lastHistoricalPrediction != null) {
      forecastBridgeValues[history.length - 1] = lastHistoricalPrediction
      forecastBridgeValues[history.length] = Number(forecast[0].forecastValue)
    }
  }
  const forecastStart = forecast[0]?.forecastDate
  resultChart.setOption(
    {
      color: ['#3b82f6', '#10b981'],
      animationDuration: 650,
      legend: {
        bottom: 4,
        left: 'center',
        itemWidth: 18,
        itemHeight: 3,
        textStyle: { color: '#64748b' },
        data: ['历史实际值', '历史预测值', '未来预测值']
      },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(15, 23, 42, .92)',
        borderWidth: 0,
        padding: [10, 12],
        textStyle: { color: '#fff' },
        axisPointer: { type: 'line', lineStyle: { color: '#94a3b8', type: 'solid' } }
      },
      grid: { left: 62, right: 30, top: 48, bottom: dates.length > 45 ? 88 : 58 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: dates,
        axisLine: { lineStyle: { color: '#dbe4f0' } },
        axisTick: { show: false },
        axisLabel: {
          color: '#64748b',
          interval: 0,
          hideOverlap: false,
          rotate: dates.length > 20 ? 45 : 0,
          margin: 14,
          formatter: (value: string) => value.slice(5)
        }
      },
      yAxis: {
        type: 'value',
        name: '用气量',
        scale: true,
        nameGap: 18,
        nameTextStyle: { color: '#94a3b8' },
        splitNumber: 5,
        splitLine: { lineStyle: { color: '#edf2f7' } },
        axisLabel: { color: '#64748b' }
      },
      dataZoom:
        dates.length > 60
          ? [
              { type: 'inside', start: 20, end: 100 },
              {
                type: 'slider',
                height: 16,
                bottom: 28,
                borderColor: 'transparent',
                backgroundColor: '#f1f5f9',
                fillerColor: 'rgba(59, 130, 246, .12)',
                handleStyle: { color: '#93c5fd' }
              }
            ]
          : [],
      series: [
        {
          name: '历史实际值',
          type: 'line',
          smooth: 0.25,
          connectNulls: false,
          showSymbol: false,
          data: historyValues,
          lineStyle: { width: 2.5, color: '#3b82f6' },
          itemStyle: { color: '#3b82f6' }
        },
        {
          name: '历史预测值',
          type: 'line',
          smooth: 0.25,
          connectNulls: false,
          showSymbol: false,
          symbolSize: 5,
          data: historicalForecastValues,
          lineStyle: { width: 2.25, color: '#f59e0b', type: 'solid' },
          itemStyle: { color: '#f59e0b' }
        },
        {
          name: '预测衔接',
          type: 'line',
          smooth: false,
          connectNulls: false,
          showSymbol: false,
          silent: true,
          tooltip: { show: false },
          data: forecastBridgeValues,
          lineStyle: { width: 2, color: '#64748b', type: 'solid' },
          emphasis: { disabled: true }
        },
        {
          name: '未来预测值',
          type: 'line',
          smooth: 0.25,
          connectNulls: false,
          showSymbol: false,
          data: futureForecastValues,
          lineStyle: { width: 2.5, color: '#10b981' },
          itemStyle: { color: '#10b981' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(16, 185, 129, .14)' },
              { offset: 1, color: 'rgba(16, 185, 129, 0)' }
            ])
          },
          markLine: forecastStart
            ? {
                silent: true,
                symbol: 'none',
                label: {
                  formatter: '未来预测起点',
                  color: '#3b82f6',
                  backgroundColor: '#dbeafe',
                  borderRadius: 4,
                  padding: [4, 7]
                },
                lineStyle: { color: '#60a5fa', type: 'solid', width: 1.5 },
                data: [{ xAxis: forecastStart }]
              }
            : undefined
        }
      ]
    },
    true
  )
}
const handleResultViewChange = () => {
  resultPage.value = 1
  void loadResultRows()
}
const handleDetailTabChange = (name: string | number) => {
  if (name !== 'result' || resultView.value !== 'chart') return
  void nextTick(() => {
    renderResultChart()
    resultChart?.resize()
  })
}
const openPredict = (row: Record<string, any>) => {
  pendingForecast.value = row
  featureDataJson.value = '[\n  {\n    "date": ""\n  }\n]'
  predictVisible.value = true
}
const confirmPredict = async () => {
  let dataset: unknown
  try {
    dataset = JSON.parse(featureDataJson.value)
  } catch {
    ElMessage.error('特征数据不是有效的 JSON')
    return
  }
  if (!Array.isArray(dataset) || dataset.length === 0) {
    ElMessage.error('特征数据必须是非空 JSON 数组')
    return
  }
  if (
    dataset.some(
      (item) => !item || typeof item !== 'object' || !String((item as Record<string, unknown>).date || '').trim()
    )
  ) {
    ElMessage.error('每条特征数据都必须包含 date 字段')
    return
  }
  if (dataset.some((item) => !/^\d{4}-\d{2}-\d{2}$/.test(String((item as Record<string, unknown>).date)))) {
    ElMessage.error('date 格式必须为 YYYY-MM-DD')
    return
  }
  const row = pendingForecast.value
  if (!row) return
  const forecastId = Number(row.forecastId || row.id)
  runningCode.value = forecastId
  try {
    const response = await postJson('/model-forecast-execution/execute', { forecastId, dataset })
    const data = response.data || {}
    predictVisible.value = false
    ElMessage.success(`预测成功：${data.resultCount || 0} 条结果`)
    if (currentForecastId.value === forecastId) await loadRecords()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预测失败')
    if (currentForecastId.value === forecastId) await loadRecords()
  } finally {
    runningCode.value = null
  }
}
const retryRecord = async (row: Record<string, any>) => {
  if (runningCode.value !== null) return
  const forecastId = Number(row.forecastId || selectedConfig.value?.id)
  const forecastBatchNo = String(row.forecastBatchNo || '')
  if (!forecastId || !forecastBatchNo) {
    ElMessage.error('预测记录信息不完整，无法重新预测')
    return
  }
  runningCode.value = forecastId
  try {
    const featureResponse = await postJson('/model-forecast-record/features', { forecastBatchNo })
    const dataset = Array.isArray(featureResponse.data) ? featureResponse.data : []
    if (!dataset.length) {
      ElMessage.error('该预测批次没有可复用的特征数据')
      return
    }
    const response = await postJson('/model-forecast-execution/execute', {
      forecastId,
      dataset,
      retryBatchNo: forecastBatchNo
    })
    const data = response.data || {}
    ElMessage.success(`重新预测成功：${data.resultCount || 0} 条结果`)
    if (currentForecastId.value === forecastId) await loadRecords()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重新预测失败')
    if (currentForecastId.value === forecastId) await loadRecords()
  } finally {
    runningCode.value = null
  }
}
onMounted(loadData)
onBeforeUnmount(() => resultChart?.dispose())
watch(recordKeyword, () => {
  recordPage.value = 1
})
</script>

<style scoped>
.forecast-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.forecast-editor-head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.forecast-editor-head h2,
.forecast-editor-head p {
  margin: 0;
}

.forecast-editor-head h2 {
  color: #172033;
  font-size: 20px;
}

.forecast-editor-head p {
  margin-top: 5px;
  color: #8490a5;
  font-size: 13px;
}

.forecast-editor-icon,
.train-config-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #2878e8;
  background: #edf5ff;
  border: 1px solid #d9eaff;
  border-radius: 12px;
}

.forecast-editor-icon {
  width: 46px;
  height: 46px;
  font-size: 23px;
}

.forecast-editor-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.forecast-editor-section {
  padding: 18px 20px 4px;
  background: #fbfcfe;
  border: 1px solid #e5eaf2;
  border-radius: 12px;
}

.forecast-section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 17px;
}

.forecast-section-head > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: #2878e8;
  font-size: 12px;
  font-weight: 700;
  background: #eaf3ff;
  border-radius: 9px;
}

.forecast-section-head > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.forecast-section-head strong {
  color: #273247;
  font-size: 15px;
}

.forecast-section-head small {
  color: #8b96a9;
  font-size: 12px;
}

.train-config-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 16px;
  padding: 12px 14px;
  background: linear-gradient(90deg, #f0f7ff, #f9fbff);
  border: 1px solid #d7e8ff;
  border-radius: 10px;
}

.train-config-icon {
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  font-size: 19px;
}

.train-config-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.train-config-main strong {
  overflow: hidden;
  color: #26324a;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.train-config-main span,
.status-switch-row span,
.field-help {
  color: #7b879d;
  font-size: 12px;
}

.train-scope-tags {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  flex-wrap: wrap;
}

.strategy-grid :deep(.el-form-item) {
  position: relative;
}

.field-help {
  position: absolute;
  right: 46px;
  bottom: 9px;
  pointer-events: none;
}

.status-switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 32px;
}

.compact-section {
  padding-bottom: 2px;
}

.remark-item {
  margin-bottom: 16px;
}

:deep(.forecast-config-dialog .el-dialog__header) {
  margin: 0;
  padding: 20px 22px 17px;
  border-bottom: 1px solid #e7ebf1;
}

:deep(.forecast-config-dialog .el-dialog__body) {
  max-height: 68vh;
  overflow-y: auto;
  padding: 18px 22px 8px;
  background: #f7f9fc;
}

:deep(.forecast-config-dialog .el-dialog__footer) {
  padding: 16px 22px 18px;
  border-top: 1px solid #e7ebf1;
  background: #fff;
}
:deep(.forecast-config-dialog .el-select),
:deep(.forecast-config-dialog .el-date-editor),
:deep(.forecast-config-dialog .el-input-number) {
  width: 100%;
}
.feature-json-input {
  margin-top: 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.panel-title h3 {
  margin: 0;
  font-size: 18px;
}
.panel-title p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
}
.inline-detail {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.inline-detail h4 {
  margin: 0 0 12px;
  font-size: 16px;
}
.inline-detail h4:not(:first-child) {
  margin-top: 20px;
}
:deep(.instances-dialog .el-dialog__header) {
  position: relative;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  min-height: 64px;
  margin: 0;
  padding: 12px 56px 12px 20px;
  border-bottom: 1px solid #dce6f2;
  background: linear-gradient(110deg, var(--app-primary-soft) 0%, #f3f7fc 68%, #edf3fa 100%);
}
:deep(.instances-dialog .el-dialog__headerbtn) {
  top: 50%;
  right: 18px;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  transform: translateY(-50%);
}
:deep(.instances-dialog .el-dialog__headerbtn:hover) {
  background: rgba(22, 119, 255, 0.08);
}
:deep(.instances-dialog .el-dialog__title) {
  color: #14233b;
  font-weight: 700;
}
:deep(.instances-dialog .el-dialog__body) {
  height: calc(100vh - 64px);
  padding: 0;
  overflow: hidden;
}
.instances-workbench {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  height: 100%;
  background: var(--app-bg);
}
.forecast-result-heading h2,
.forecast-result-heading p {
  margin: 0;
}
.forecast-result-heading h2 {
  color: #101828;
  font-size: 20px;
  font-weight: 700;
  line-height: 28px;
}
.forecast-result-heading p {
  margin-top: 3px;
  color: #667085;
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
}
.instances-sidebar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  padding: 16px;
  overflow: auto;
  background: #f8fafc;
  border-right: 1px solid var(--app-border);
}
.result-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #344054;
  font-size: 13px;
}
.result-sidebar-head strong {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #344054;
  font-weight: 700;
}
.result-sidebar-head span {
  color: #667085;
  font-size: 12px;
}
.result-sidebar-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 34px;
  gap: 8px;
}
.result-sidebar-search :deep(.el-input__wrapper) {
  min-height: 34px;
  border-radius: 6px;
  box-shadow: 0 0 0 1px #d0d5dd inset;
}
.result-sidebar-search :deep(.el-input__inner) {
  font-size: 12px;
}
.result-sidebar-search .el-button {
  width: 34px;
  height: 34px;
  padding: 0;
  border-radius: 6px;
}
.result-sidebar-tabs {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 28px;
  border-bottom: 1px solid #eaecf0;
}
.result-sidebar-tabs button {
  position: relative;
  height: 28px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.result-sidebar-tabs button.active {
  color: var(--app-primary);
}
.result-sidebar-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  border-radius: 999px;
  background: var(--app-primary);
  content: '';
}
.instance-list {
  flex: 0 0 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.instance-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 12px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #fff;
  color: #475467;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}
.instance-card:hover {
  border-color: #bfd8ff;
  box-shadow: 0 4px 12px rgba(47, 128, 237, 0.06);
}
.instance-card.active {
  border-color: #9fc5ff;
  background: #f4f8ff;
  box-shadow: inset 2px 0 0 #3b82f6;
}
.instance-card > div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.instance-card strong {
  min-width: 0;
  overflow: hidden;
  color: #182230;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.instance-card p {
  margin: 0;
  color: #344054;
  font-size: 12px;
  font-weight: 600;
}
.instance-card small {
  color: #667085;
  font-weight: 400;
}
.forecast-status-tag {
  border: 0 !important;
  border-radius: 4px !important;
  font-weight: 600;
}
.forecast-status-tag.status-1 {
  background: #dbeafe !important;
  color: #3b82f6 !important;
}
.forecast-status-tag.status-2 {
  background: #dcfce7 !important;
  color: #22b85b !important;
}
.forecast-status-tag.status-3 {
  background: #fee2e2 !important;
  color: #ef4444 !important;
}
.result-sidebar-pagination {
  flex: 0 0 auto;
  margin-top: 2px;
}
.result-sidebar-pagination :deep(.el-pagination) {
  justify-content: center;
  gap: 4px;
  white-space: normal;
}
.result-sidebar-pagination :deep(.el-pagination__total),
.result-sidebar-pagination :deep(.el-pagination__sizes) {
  display: none;
}
.result-sidebar-pagination :deep(.el-pager li),
.result-sidebar-pagination :deep(.btn-prev),
.result-sidebar-pagination :deep(.btn-next) {
  min-width: 26px;
  height: 26px;
  font-size: 12px;
}
.instance-detail {
  min-width: 0;
  overflow-y: auto;
  padding: 16px 18px 28px;
  background: var(--app-bg);
}
.panel-title {
  min-height: 58px;
  margin: 0 0 12px;
  padding: 0 2px;
  border: 0;
  background: transparent;
}
.detail-title-line {
  display: flex;
  align-items: center;
  gap: 10px;
}
.panel-title h3 {
  color: #101828;
  font-size: 20px;
  font-weight: 700;
}
.panel-title p {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 5px;
  color: #667085;
  font-size: 12px;
  font-weight: 400;
}
.panel-title p span:first-child {
  color: #475467;
  font-weight: 600;
}
.detail-card {
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-surface);
  box-shadow: 0 2px 10px rgba(31, 64, 104, 0.035);
}
.forecast-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
.forecast-summary-card {
  min-width: 0;
  padding: 16px 18px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-surface);
  box-shadow: 0 2px 8px rgba(31, 64, 104, 0.035);
}
.forecast-summary-card h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  color: #27364d;
  font-size: 14px;
  font-weight: 700;
}
.forecast-summary-card h4 > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: #eaf3ff;
  color: var(--app-primary);
  font-size: 15px;
}
.forecast-summary-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
}
.forecast-summary-item {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.forecast-summary-item > span {
  color: #667085;
  font-size: 12px;
  font-weight: 500;
}
.forecast-summary-item > strong {
  overflow: hidden;
  color: #1f2937;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.section-title {
  position: relative;
  margin: 0 0 16px;
  padding-left: 11px;
  color: #20324d;
  font-size: 15px;
  font-weight: 700;
}
.section-title::before {
  position: absolute;
  top: 2px;
  bottom: 2px;
  left: 0;
  width: 3px;
  border-radius: 4px;
  background: linear-gradient(180deg, var(--app-primary), #62a6ff);
  content: '';
}
.forecast-overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: 32px;
  row-gap: 12px;
}
.forecast-overview-item {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  align-items: baseline;
  column-gap: 12px;
  min-width: 0;
}
.forecast-overview-item > span {
  color: #667085;
  font-size: 12px;
  font-weight: 400;
}
.forecast-overview-item > strong {
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.forecast-overview-item > .forecast-status-tag {
  width: fit-content;
  justify-self: start;
}
.forecast-data-card {
  padding: 0 12px 12px;
}
.forecast-data-tabs :deep(.el-tabs__header) {
  margin: 0 0 18px;
}
.forecast-data-tabs {
  position: relative;
}
.forecast-data-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #e8eef7;
}
.forecast-data-tabs :deep(.el-tabs__item) {
  height: 48px;
  padding: 0 24px;
  color: #667085;
  font-size: 14px;
  font-weight: 600;
}
.forecast-data-tabs :deep(.el-tabs__item.is-active) {
  color: var(--app-primary);
  font-weight: 700;
}
.forecast-data-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--app-primary);
}
.forecast-data-tabs :deep(.el-table) {
  margin-top: 2px;
}
.result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.result-heading .section-title {
  margin-bottom: 0;
}
.result-heading :deep(.el-radio-button__inner) {
  border-color: #dce7f5;
  color: #607089;
}
.result-heading > .el-radio-group {
  position: absolute;
  top: 8px;
  right: 0;
  z-index: 2;
}
.result-heading :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: var(--app-primary);
  background: var(--app-primary);
  box-shadow: -1px 0 0 0 var(--app-primary);
  color: #fff;
}
.result-chart {
  width: 100%;
  height: 410px;
  background: #fff;
  border: 1px solid #edf2f7;
  border-radius: 8px;
}
@media (max-width: 800px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .instances-workbench {
    grid-template-columns: 1fr;
    overflow-y: auto;
  }
  .instances-sidebar {
    min-height: 380px;
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .instance-detail {
    overflow: visible;
  }
  .forecast-overview-grid {
    grid-template-columns: 1fr;
  }
  .forecast-summary-grid {
    grid-template-columns: 1fr;
  }
  :deep(.forecast-config-dialog) {
    width: calc(100vw - 24px) !important;
  }
}
</style>
