<template>
  <section class="crud-page">
    <PageBreadcrumb :description="config.pageDescription" />

    <AppTablePanel>
      <template #filters>
        <el-input
          v-model="keyword"
          class="search-input"
          clearable
          :prefix-icon="Search"
          :placeholder="config.searchPlaceholder"
          @clear="searchData"
          @keyup.enter="searchData"
        />
        <template v-for="field in config.filterFields || []" :key="field.prop">
          <el-date-picker
            v-if="field.type === 'dateRange'"
            v-model="filters[field.prop]"
            class="filter-control"
            type="daterange"
            unlink-panels
            clearable
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :style="{ width: `${field.width || 260}px` }"
            @change="searchData"
          />
          <el-select
            v-else-if="field.type === 'select'"
            v-model="filters[field.prop]"
            class="filter-control"
            clearable
            :placeholder="field.placeholder || field.label"
            :style="{ width: `${field.width || 160}px` }"
            @change="searchData"
            @clear="searchData"
          >
            <el-option
              v-for="option in field.options || []"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-else
            v-model="filters[field.prop]"
            class="filter-control"
            clearable
            :placeholder="field.placeholder || field.label"
            :style="{ width: `${field.width || 180}px` }"
            @clear="searchData"
            @keyup.enter="searchData"
          />
        </template>
        <el-button type="primary" :icon="Search" @click="searchData">查询</el-button>
        <el-button type="info" plain @click="resetAllSearch">重置</el-button>
      </template>

      <template #actions>
        <slot name="table-actions" :reload="loadData" />
        <PermissionButton
          v-if="!config.readonly"
          type="primary"
          :icon="Plus"
          :permission="config.permissions?.create"
          @click="openCreate"
          >新增</PermissionButton
        >
      </template>

      <AppTable v-loading="loading" :data="records" stripe border @sort-change="handleSortChange">
        <el-table-column
          type="index"
          :index="rowIndex"
          label="序号"
          width="72"
          fixed
          class-name="id-column"
          label-class-name="id-column"
        />
        <el-table-column
          v-for="field in config.tableFields"
          :key="field.prop"
          :prop="field.prop"
          :label="field.label"
          :min-width="field.minWidth || 120"
          :width="field.width"
          :align="field.align"
          :sortable="field.sortable"
          show-overflow-tooltip
        >
          <template #header>
            <el-tooltip v-if="field.tooltip" :content="field.tooltip" placement="top">
              <span class="column-header-with-tip">{{ field.label }}</span>
            </el-tooltip>
            <span v-else>{{ field.label }}</span>
          </template>
          <template #default="{ row }">
            <ManagementTableCell :field="field" :row="row" />
          </template>
        </el-table-column>
        <el-table-column
          v-if="!config.readonly"
          label="操作"
          :width="config.trainExecution ? 260 : 150"
          fixed="right"
          class-name="action-column"
          label-class-name="action-column"
        >
          <template #default="{ row }">
            <el-button v-if="config.trainExecution" link type="success" @click="openExecute(row)">执行</el-button>
            <el-button v-if="config.trainExecution" link type="primary" :icon="View" @click="openTrainResult(row)"
              >训练结果</el-button
            >
            <PermissionButton link type="primary" :permission="config.permissions?.update" @click="openEdit(row)"
              >编辑</PermissionButton
            >
            <PermissionButton link type="danger" :permission="config.permissions?.delete" @click="removeRow(row)"
              >删除</PermissionButton
            >
          </template>
        </el-table-column>
        <el-table-column
          v-else-if="config.featureDetailProp"
          label="操作"
          width="110"
          fixed="right"
          class-name="action-column"
          label-class-name="action-column"
        >
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openFeatureDetail(row)">查看特征</el-button>
          </template>
        </el-table-column>
      </AppTable>

      <template #footer>
        <AppPagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </template>
    </AppTablePanel>

    <AppDialog
      v-model="dialogVisible"
      eyebrow="后台数据管理"
      :title="dialogTitle"
      :width="config.dialogWidth || '680px'"
      :class="{
        'feature-definition-dialog': config.dialogVariant === 'feature-definition',
        'model-training-dialog': config.dialogVariant === 'model-training'
      }"
      align-center
    >
      <template v-if="config.dialogVariant === 'feature-definition'" #header>
        <div class="feature-dialog-header">
          <div class="feature-dialog-icon">
            <el-icon><DocumentAdd /></el-icon>
          </div>
          <div>
            <p>后台数据管理</p>
            <h2>{{ dialogTitle }}</h2>
            <span>{{ config.dialogDescription }}</span>
          </div>
        </div>
      </template>
      <template v-else-if="config.dialogVariant === 'model-training'" #header>
        <div class="feature-dialog-header">
          <div class="feature-dialog-icon">
            <el-icon><DataLine /></el-icon>
          </div>
          <div>
            <p>后台数据管理</p>
            <h2>{{ dialogTitle }}</h2>
            <span>{{ config.dialogDescription }}</span>
          </div>
        </div>
      </template>
      <el-form ref="formRef" class="dialog-form" :model="form" :rules="formRules" label-position="top">
        <el-form-item
          v-for="field in visibleFormFields"
          :key="field.prop"
          :label="field.label"
          :prop="field.prop"
          :class="{ 'form-item-full': field.fullWidth }"
        >
          <FormFieldRenderer
            :field="field"
            :model="form"
            :option-map="resolvedFormOptionMap"
            @option-select="handleFormOptionSelect"
          />
          <p v-if="field.helperText" class="field-helper">{{ field.helperText }}</p>
        </el-form-item>
      </el-form>
      <section v-if="config.trainExecution" class="training-requirement-card" v-loading="platformModelsLoading">
        <template v-if="selectedPlatformModel">
          <div class="requirement-title">
            <span>当前模型数据要求</span>
            <el-tag type="primary" effect="light">{{ selectedPlatformModel.model_name }}</el-tag>
          </div>
          <p>{{ selectedPlatformModel.training_data_range?.description || '模型平台未提供详细的数据范围说明。' }}</p>
          <div v-if="selectedPlatformModel.training_data_range" class="requirement-grid">
            <span
              ><small>数据频率</small
              ><strong>{{ platformFrequencyText(selectedPlatformModel.training_data_range.frequency) }}</strong></span
            >
            <span
              ><small>最低数量</small
              ><strong>{{ selectedPlatformModel.training_data_range.minimum }} 个周期</strong></span
            >
            <span
              ><small>建议数量</small
              ><strong>{{
                selectedPlatformModel.training_data_range.recommended
                  ? `${selectedPlatformModel.training_data_range.recommended} 个周期`
                  : '-'
              }}</strong></span
            >
            <span
              ><small>连续性</small
              ><strong>{{
                selectedPlatformModel.training_data_range.continuous === true
                  ? '必须连续'
                  : selectedPlatformModel.training_data_range.continuous === false
                    ? '允许不连续'
                    : '-'
              }}</strong></span
            >
          </div>
        </template>
        <el-empty v-else :image-size="46" description="选择模型后显示训练数据要求" />
      </section>
      <template #footer>
        <el-button type="info" plain @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="executionMode ? continueExecution() : saveData()">{{
          executionMode ? '继续执行' : '保存'
        }}</el-button>
      </template>
    </AppDialog>

    <AppDialog v-model="previewVisible" eyebrow="模型训练执行" title="训练数据预览" width="1120px" align-center>
      <div class="preview-summary">
        <span>训练配置编码：{{ executionConfig?.trainCode || '-' }}</span>
        <span>时间格式：{{ granularityText(executionConfig?.timeGranularity) }}</span>
        <span>训练方式：{{ executionConfig?.trainMode === 'RANGE' ? '指定时间范围' : '最近时间' }}</span>
        <span>数据量：{{ previewTotal }}</span>
      </div>
      <div class="preview-scope">
        <span>训练区域：{{ executionConfig?.regionName || '全部' }}</span>
        <span>训练行业：{{ executionConfig?.industryName || '全部' }}</span>
        <span>训练客户：{{ executionConfig?.customerName || '全部' }}</span>
        <span>时间范围：{{ previewTimeRange }}</span>
      </div>
      <el-table v-loading="previewLoading" :data="previewRows" border stripe height="430px">
        <el-table-column prop="statDate" label="统计日期" min-width="110" fixed />
        <el-table-column prop="timeGranularity" label="时间格式" min-width="90">
          <template #default="{ row }">{{ granularityText(row.timeGranularity) }}</template>
        </el-table-column>
        <el-table-column prop="regionName" label="区域" min-width="110" />
        <el-table-column prop="industryName" label="行业" min-width="110" />
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column prop="gasSales" label="天然气销量" min-width="120" align="right" />
        <el-table-column prop="featureDetails" label="特征数量" min-width="90" align="right">
          <template #default="{ row }">{{
            Array.isArray(row.featureDetails) ? row.featureDetails.length : 0
          }}</template>
        </el-table-column>
      </el-table>
      <div class="preview-pagination">
        <AppPagination
          v-model:current-page="previewPage"
          v-model:page-size="previewSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="previewTotal"
          @current-change="loadPreviewData"
          @size-change="handlePreviewSizeChange"
        />
      </div>
      <template #footer>
        <el-button type="info" plain @click="previewVisible = false">取消</el-button>
        <el-button type="primary" :loading="executing" :disabled="previewRows.length === 0" @click="executeTraining"
          >确认执行</el-button
        >
      </template>
    </AppDialog>

    <el-drawer v-model="featureDetailVisible" title="特征详情" size="520px">
      <div v-if="selectedRow" class="feature-detail">
        <div class="feature-summary">
          <span>{{ selectedRow.statDate || '-' }}</span>
          <span>{{ selectedRow.regionName || '-' }}</span>
          <span>{{ selectedRow.industryName || '-' }}</span>
        </div>
        <el-table
          v-if="featureDetailEntries.length"
          :data="featureDetailEntries"
          border
          stripe
          height="calc(100vh - 190px)"
        >
          <el-table-column prop="featureNo" label="特征编号" min-width="120" />
          <el-table-column prop="featureCode" label="特征编码" min-width="160" show-overflow-tooltip />
          <el-table-column prop="featureValue" label="特征值" min-width="120" align="right" />
        </el-table>
        <el-empty v-if="!featureDetailEntries.length" description="暂无特征值" />
      </div>
    </el-drawer>

    <el-dialog v-model="trainResultVisible" fullscreen class="train-result-dialog" destroy-on-close>
      <template #header>
        <div class="train-result-heading">
          <h2>训练结果</h2>
          <p>查看训练批次、指标表现及结果明细</p>
        </div>
      </template>
      <div v-loading="trainResultLoading" class="train-result">
        <div v-if="trainResultBatches.length" class="train-result-layout">
          <aside class="train-result-sidebar">
            <div class="result-sidebar-head">
              <strong
                ><el-icon><Tickets /></el-icon>训练批次</strong
              >
              <span>{{ filteredTrainResultBatches.length }} / {{ trainResultBatches.length }} 条</span>
            </div>
            <div class="result-sidebar-search">
              <el-input
                v-model="trainBatchKeyword"
                clearable
                :prefix-icon="Search"
                placeholder="请输入批次号/模型名称"
              />
              <el-button :icon="Filter" />
            </div>
            <div class="result-sidebar-tabs">
              <button
                v-for="tab in trainBatchStatusTabs"
                :key="tab.value"
                type="button"
                :class="{ active: trainBatchStatus === tab.value }"
                @click="trainBatchStatus = tab.value"
              >
                {{ tab.label }}
              </button>
            </div>
            <div class="train-batch-list">
              <div
                v-for="batch in pagedTrainResultBatches"
                :key="batch.batchNo"
                class="result-batch-card"
                :class="{ active: batch.batchNo === selectedTrainBatchNo }"
                role="button"
                tabindex="0"
                @click="handleTrainResultSelect(batch)"
                @keydown.enter="handleTrainResultSelect(batch)"
              >
                <span class="batch-card-top">
                  <strong>{{ batch.batchNo || '-' }}</strong>
                  <el-tag
                    size="small"
                    :type="statusTagType(batch.status)"
                    :class="['train-status-tag', trainStatusClass(batch.status)]"
                    effect="light"
                    >{{ statusText(batch.status) }}</el-tag
                  >
                </span>
                <span>{{ batch.bestModel || '暂无最佳模型' }}</span>
                <small>{{ batch.updatedAt || batch.createdAt || '-' }}</small>
                <el-button
                  v-if="isBatchFailed(batch.status)"
                  link
                  type="warning"
                  :loading="retrainingBatchNo === batch.batchNo"
                  @click.stop="retrainBatch(batch)"
                >
                  重新训练
                </el-button>
              </div>
              <el-empty
                v-if="!pagedTrainResultBatches.length"
                class="result-sidebar-empty"
                description="暂无匹配批次"
              />
            </div>
            <AppPagination
              v-if="filteredTrainResultBatches.length"
              v-model:current-page="trainBatchPage"
              v-model:page-size="trainBatchSize"
              class="result-sidebar-pagination"
              :page-sizes="[5, 10, 20]"
              :total="filteredTrainResultBatches.length"
              @size-change="handleTrainBatchSizeChange"
            />
          </aside>

          <main v-loading="trainResultDetailLoading" class="train-result-main">
            <section class="result-hero">
              <div class="result-hero-content">
                <div class="result-hero-title">
                  <strong>{{ trainResultSource?.trainName || '训练详情' }}</strong>
                  <el-tag
                    size="small"
                    :type="statusTagType(selectedTrainResult?.status)"
                    :class="['train-status-tag', trainStatusClass(selectedTrainResult?.status)]"
                    effect="light"
                    >{{ statusText(selectedTrainResult?.status) }}</el-tag
                  >
                </div>
                <div class="result-hero-subtitle">
                  <span>{{ selectedTrainBatchNo || '-' }}</span>
                  <span>创建时间：{{ selectedTrainResult?.createdAt || '-' }}</span>
                  <span>完成时间：{{ selectedTrainResult?.updatedAt || '-' }}</span>
                </div>
              </div>
            </section>

            <section class="train-summary-grid">
              <article v-for="group in trainSummaryGroups" :key="group.title" class="train-summary-card">
                <h4>
                  <span
                    ><el-icon><component :is="group.icon" /></el-icon></span
                  >{{ group.title }}
                </h4>
                <div class="train-summary-list">
                  <div v-for="item in group.items" :key="item.label" class="train-summary-item">
                    <span>{{ item.label }}</span>
                    <strong :class="item.tone" :title="String(item.value)">{{ item.value }}</strong>
                  </div>
                </div>
              </article>
            </section>
            <el-alert
              v-if="trainResultErrorMessage"
              title="训练失败信息"
              :description="trainResultErrorMessage"
              type="error"
              :closable="false"
              show-icon
            />

            <section class="result-metrics-panel">
              <h4 class="train-section-title">
                <span
                  ><el-icon><TrendCharts /></el-icon></span
                >模型评估指标
              </h4>
              <div class="result-metric-grid">
                <div
                  v-for="metric in resultMetricCards"
                  :key="metric.label"
                  class="result-metric-card"
                  :class="metric.tone"
                >
                  <div class="metric-card-head">
                    <el-icon><component :is="metric.icon" /></el-icon>
                    <span>{{ metric.label }}</span>
                  </div>
                  <strong>{{ metric.value }}</strong>
                  <small>{{ metric.hint }}</small>
                </div>
              </div>
            </section>

            <el-tabs v-model="activeTrainResultTab" class="result-tabs">
              <el-tab-pane name="dataset" lazy>
                <template #label
                  ><span class="result-tab-label"
                    ><el-icon><DataLine /></el-icon>训练数据</span
                  ></template
                >
                <template v-if="requestDatasetRows.length">
                  <el-table :data="pagedRequestDatasetRows" border stripe height="320px">
                    <el-table-column
                      v-for="column in requestDatasetColumns"
                      :key="column"
                      :prop="column"
                      :label="column"
                      min-width="120"
                      show-overflow-tooltip
                    />
                  </el-table>
                  <AppPagination
                    v-if="requestDatasetRows.length > requestDatasetSize"
                    v-model:current-page="requestDatasetPage"
                    v-model:page-size="requestDatasetSize"
                    :page-sizes="[20, 50, 100, 200]"
                    :total="requestDatasetRows.length"
                  />
                </template>
                <el-empty v-else description="暂无训练数据" />
              </el-tab-pane>
              <el-tab-pane name="metrics" lazy>
                <template #label
                  ><span class="result-tab-label"
                    ><el-icon><Histogram /></el-icon>指标概览</span
                  ></template
                >
                <el-table v-if="resultMetricRows.length" :data="resultMetricRows" border stripe height="220px">
                  <el-table-column prop="label" label="指标" min-width="180" />
                  <el-table-column prop="value" label="值" min-width="240" />
                </el-table>
                <el-empty v-else description="暂无指标" />
              </el-tab-pane>
              <el-tab-pane name="candidates" lazy>
                <template #label
                  ><span class="result-tab-label"
                    ><el-icon><Cpu /></el-icon>候选模型</span
                  ></template
                >
                <el-table v-if="candidateRows.length" :data="candidateRows" border stripe height="320px">
                  <el-table-column prop="rank" label="排名" width="80" />
                  <el-table-column prop="model_name" label="模型名称" min-width="180" show-overflow-tooltip />
                  <el-table-column prop="model_type" label="类型" min-width="110" />
                  <el-table-column prop="selected" label="选中" min-width="90" />
                  <el-table-column prop="score" label="综合评分" min-width="110" align="right" />
                  <el-table-column prop="rmse" label="RMSE" min-width="110" align="right" />
                  <el-table-column prop="mape" label="MAPE" min-width="100" align="right" />
                  <el-table-column prop="wmape" label="WMAPE" min-width="100" align="right" />
                  <el-table-column prop="mae" label="MAE" min-width="100" align="right" />
                  <el-table-column prop="r2" label="R²" min-width="90" align="right" />
                </el-table>
                <el-empty v-else description="暂无候选模型" />
              </el-tab-pane>
              <el-tab-pane name="backtests" lazy>
                <template #label
                  ><span class="result-tab-label"
                    ><el-icon><TrendCharts /></el-icon>回测结果</span
                  ></template
                >
                <div v-if="backtestRows.length" class="result-view-toolbar">
                  <el-radio-group v-model="backtestResultView" size="small">
                    <el-radio-button value="table">数据</el-radio-button>
                    <el-radio-button value="chart">图表</el-radio-button>
                  </el-radio-group>
                </div>
                <el-table
                  v-if="backtestRows.length && backtestResultView === 'table'"
                  :data="backtestRows"
                  border
                  stripe
                  height="320px"
                >
                  <el-table-column prop="stat_date" label="日期" min-width="110" fixed />
                  <el-table-column prop="model_name" label="模型" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="season" label="周期" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="actual_value" label="实际值" min-width="110" align="right" />
                  <el-table-column prop="predicted_value" label="预测值" min-width="110" align="right" />
                  <el-table-column prop="absolute_error" label="绝对误差" min-width="110" align="right" />
                  <el-table-column prop="error_rate" label="误差率" min-width="100" align="right" />
                </el-table>
                <div v-else-if="backtestRows.length" ref="backtestChartRef" class="result-chart" />
                <el-empty v-else description="暂无回测结果" />
              </el-tab-pane>
              <el-tab-pane name="issues" lazy>
                <template #label
                  ><span class="result-tab-label"
                    ><el-icon><Aim /></el-icon>问题列表</span
                  ></template
                >
                <el-table v-if="issueRows.length" :data="issueRows" border stripe height="260px">
                  <el-table-column prop="severity" label="级别" min-width="90" />
                  <el-table-column prop="stage" label="阶段" min-width="120" />
                  <el-table-column prop="category" label="类别" min-width="120" />
                  <el-table-column prop="message" label="信息" min-width="360" show-overflow-tooltip />
                </el-table>
                <el-empty v-else description="暂无问题" />
              </el-tab-pane>
              <el-tab-pane name="json" lazy>
                <template #label
                  ><span class="result-tab-label"
                    ><el-icon><Document /></el-icon>原始 JSON</span
                  ></template
                >
                <el-alert
                  v-if="rawJsonTruncated"
                  title="为避免页面卡顿，超大数组仅展示前 100 条；完整训练数据可在“训练数据”页签中分页查看。"
                  type="info"
                  :closable="false"
                  show-icon
                />
                <div class="json-grid">
                  <section>
                    <div class="result-subtitle">请求 JSON</div>
                    <pre v-if="trainRequestJsonText" class="result-json">{{ trainRequestJsonText }}</pre>
                    <el-empty v-else description="暂无请求参数" />
                  </section>
                  <section>
                    <div class="result-subtitle">结果 JSON</div>
                    <pre v-if="trainResultJsonText" class="result-json">{{ trainResultJsonText }}</pre>
                    <el-empty v-else description="暂无结果数据" />
                  </section>
                </div>
              </el-tab-pane>
            </el-tabs>
          </main>
        </div>
        <el-empty v-else-if="!trainResultLoading" description="暂无训练结果" />
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Aim,
  Cpu,
  DataLine,
  Document,
  DocumentAdd,
  Filter,
  Histogram,
  Odometer,
  PieChart,
  Plus,
  Search,
  Tickets,
  TrendCharts,
  View
} from '@element-plus/icons-vue'
import { createRow, deleteRow, getJson, listPage, postJson, updateRow, type PageRequest } from '@/api/management'
import { usePageQuery } from '@/composables/usePageQuery'
import AppDialog from '@/components/AppDialog.vue'
import AppPagination from '@/components/AppPagination.vue'
import AppTable from '@/components/AppTable.vue'
import AppTablePanel from '@/components/AppTablePanel.vue'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'
import ManagementTableCell from '@/components/ManagementTableCell.vue'
import PageBreadcrumb from '@/components/PageBreadcrumb.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import type { BaseDataFieldConfig, BaseDataPageConfig, Option } from '@/views/shared/managementTypes'

const props = defineProps<{
  pageConfig: BaseDataPageConfig
}>()

const saving = ref(false)
const executing = ref(false)
const retrainingBatchNo = ref('')
const dialogVisible = ref(false)
const previewVisible = ref(false)
const previewLoading = ref(false)
const executionMode = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<Record<string, any>>({})
const filters = reactive<Record<string, any>>({})
const sortField = ref('')
const sortOrder = ref('')
const featureDetailVisible = ref(false)
const trainResultVisible = ref(false)
const trainResultLoading = ref(false)
const trainResultDetailLoading = ref(false)
const selectedRow = ref<Record<string, any> | null>(null)
const executionConfig = ref<Record<string, any> | null>(null)
const trainResultSource = ref<Record<string, any> | null>(null)
const trainResultBatches = ref<Record<string, any>[]>([])
const selectedTrainBatchNo = ref('')
const trainBatchKeyword = ref('')
const trainBatchStatus = ref<'all' | 'running' | 'success' | 'failed'>('all')
const trainBatchPage = ref(1)
const trainBatchSize = ref(5)
const activeTrainResultTab = ref('dataset')
const backtestResultView = ref<'table' | 'chart'>('chart')
const requestDatasetPage = ref(1)
const requestDatasetSize = ref(50)
const backtestChartRef = ref<HTMLDivElement>()
const previewRows = ref<Record<string, any>[]>([])
const previewTotal = ref(0)
const previewPage = ref(1)
const previewSize = ref(20)
const formOptionMap = reactive<Record<string, Option[]>>({})
const platformModelsLoading = ref(false)
const platformModels = ref<PlatformModel[]>([])

interface PlatformModel {
  agent_code: string
  model_code: string
  model_name: string
  model_version: string
  training_data_range?: {
    type: string
    frequency: string
    minimum: number
    recommended?: number | null
    continuous?: boolean | null
    description?: string
  } | null
}

const config = computed(() => props.pageConfig)
const visibleFormFields = computed(() =>
  config.value.formFields.filter((field) => {
    if (!field.visibleWhen) {
      return true
    }
    return form[field.visibleWhen.prop] === field.visibleWhen.value
  })
)
const resolvedFormOptionMap = computed<Record<string, Option[]>>(() => {
  if (!config.value.trainExecution) {
    return formOptionMap
  }
  return {
    ...formOptionMap,
    modelId: (formOptionMap.modelId || []).filter((option) => option.raw?.agentCode === form.agentCode)
  }
})
const dialogTitle = computed(
  () => `${executionMode.value ? '执行' : editingId.value ? '编辑' : '新增'}${config.value.title.replace('管理', '')}`
)
const selectedPlatformModel = computed(
  () => platformModels.value.find((item) => item.model_code === form.modelCode) || null
)
const previewTimeRange = computed(() => {
  if (!executionConfig.value) {
    return '-'
  }
  if (executionConfig.value.trainMode === 'RECENT') {
    return `最近 ${executionConfig.value.recentPeriods || 36} 个周期`
  }
  return `${executionConfig.value.trainStartDate || '-'} 至 ${executionConfig.value.trainEndDate || '-'}`
})
const selectedTrainResult = computed(() => {
  return (
    trainResultBatches.value.find((item) => item.batchNo === selectedTrainBatchNo.value) ||
    trainResultBatches.value[0] ||
    null
  )
})
const trainBatchStatusTabs: Array<{ label: string; value: 'all' | 'running' | 'success' | 'failed' }> = [
  { label: '全部', value: 'all' },
  { label: '训练中', value: 'running' },
  { label: '训练成功', value: 'success' },
  { label: '训练失败', value: 'failed' }
]
const filteredTrainResultBatches = computed(() => {
  const keyword = trainBatchKeyword.value.trim().toLowerCase()
  return trainResultBatches.value.filter((item) => {
    const status = String(item.status || '').toUpperCase()
    if (trainBatchStatus.value === 'running' && !['PENDING', 'RUNNING'].includes(status)) {
      return false
    }
    if (trainBatchStatus.value === 'success' && status !== 'SUCCESS') {
      return false
    }
    if (trainBatchStatus.value === 'failed' && status !== 'FAILED') {
      return false
    }
    if (!keyword) {
      return true
    }
    const searchable = [
      item.batchNo,
      item.batch_no,
      item.bestModel,
      item.best_model,
      item.status,
      item.regionName,
      item.region_name,
      item.customerName,
      item.customer_name,
      item.industryName,
      item.industry_name,
      item.trainStartDate,
      item.train_start_date,
      item.trainEndDate,
      item.train_end_date
    ]
      .filter((value) => value !== undefined && value !== null)
      .join(' ')
      .toLowerCase()
    return searchable.includes(keyword)
  })
})
const pagedTrainResultBatches = computed(() => {
  const start = (trainBatchPage.value - 1) * trainBatchSize.value
  return filteredTrainResultBatches.value.slice(start, start + trainBatchSize.value)
})
const statusTagType = (status: unknown) => {
  const value = String(status || '').toUpperCase()
  if (value === 'SUCCESS') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'RUNNING' || value === 'PENDING') return 'warning'
  return 'info'
}
const statusText = (status: unknown) => {
  const value = String(status || '').toUpperCase()
  if (value === 'SUCCESS') return '训练成功'
  if (value === 'FAILED') return '训练失败'
  if (value === 'RUNNING') return '训练中'
  if (value === 'PENDING') return '训练等待中'
  return status ? String(status) : '-'
}
const trainStatusClass = (status: unknown) => `status-${String(status || 'unknown').toLowerCase()}`
const statusTone = (status: unknown) => {
  const value = String(status || '').toUpperCase()
  if (value === 'SUCCESS') return 'status-success'
  if (value === 'FAILED') return 'status-failed'
  if (value === 'RUNNING') return 'status-running'
  if (value === 'PENDING') return 'status-pending'
  return ''
}
const metricValue = (value: unknown, suffix = '') => {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) return '-'
  return `${Number(numberValue.toFixed(6))}${suffix}`
}
const textValue = (...values: unknown[]) => {
  const value = values.find((item) => item !== undefined && item !== null && String(item).trim() !== '')
  return value === undefined ? '-' : String(value)
}
const agentText = (...values: unknown[]) => {
  const value = textValue(...values)
  const labels: Record<string, string> = {
    'winter-supply': '冬季保供预测智能体',
    'winner-agent': '冬季保供预测智能体',
    'monthly-sales': '月度销量预测智能体',
    'short-term': '短期客户预测智能体'
  }
  return labels[value] || value
}
const durationText = (value: unknown) => {
  const seconds = Number(value)
  if (!Number.isFinite(seconds) || seconds < 0) return '-'
  if (seconds < 60) return `${Number(seconds.toFixed(1))} 秒`
  const minutes = Math.floor(seconds / 60)
  const remainSeconds = Math.round(seconds % 60)
  if (minutes < 60) return remainSeconds ? `${minutes} 分 ${remainSeconds} 秒` : `${minutes} 分`
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return remainMinutes ? `${hours} 小时 ${remainMinutes} 分` : `${hours} 小时`
}
const resultOverviewRows = computed(() => {
  const result = selectedTrainResult.value || {}
  const source = trainResultSource.value || {}
  const request = asRecord(result.requestJson)
  const dataset = asArray(request.dataset)
  const datasetDates = dataset
    .map((item) => textValue(item.date, item.stat_date, item.train_date))
    .filter((value) => value !== '-')
    .sort()
  const datasetTotal = Number(request.dataset_total ?? request.datasetTotal ?? dataset.length)
  const datasetTruncated = Boolean(request.dataset_truncated ?? request.datasetTruncated)
  const rows = [
    { label: '训练批次号', value: textValue(result.batchNo, result.batch_no) },
    {
      label: '智能体',
      value: agentText(
        result.agentName,
        result.agent_name,
        source.agentName,
        source.agentLabel,
        result.agentCode,
        result.agent_code,
        source.agentCode
      )
    },
    { label: '区域名称', value: textValue(result.regionName, result.region_name, source.regionName) },
    { label: '客户名称', value: textValue(result.customerName, result.customer_name, source.customerName) },
    { label: '所属行业名字', value: textValue(result.industryName, result.industry_name, source.industryName) },
    {
      label: '训练数据开始日期',
      value: textValue(datasetDates[0], result.trainStartDate, result.train_start_date, source.trainStartDate)
    },
    {
      label: '训练数据截止日期',
      value: textValue(
        datasetTruncated ? undefined : datasetDates[datasetDates.length - 1],
        result.trainEndDate,
        result.train_end_date,
        source.trainEndDate,
        datasetDates[datasetDates.length - 1]
      )
    },
    { label: '训练数据量', value: Number.isFinite(datasetTotal) && datasetTotal > 0 ? `${datasetTotal} 条` : '-' },
    { label: '训练耗时', value: durationText(result.trainDurationSeconds ?? result.train_duration_seconds) },
    { label: '训练状态', value: statusText(result.status), tone: statusTone(result.status) },
    { label: '最佳模型', value: textValue(result.bestModel, result.best_model) },
    { label: '创建人', value: textValue(result.createdBy, result.created_by) }
  ]
  const errorMessage = textValue(result.errorMessage, result.error_message)
  if (errorMessage !== '-') {
    rows.push({ label: '错误信息', value: errorMessage })
  }
  return [...rows]
})
const trainSummaryGroups = computed(() => {
  const rows = new Map(resultOverviewRows.value.map((item) => [item.label, item]))
  const pick = (...labels: string[]) => labels.map((label) => rows.get(label)).filter(Boolean)
  return [
    {
      title: '训练批次',
      icon: Tickets,
      items: pick('训练批次号', '训练数据开始日期', '训练数据截止日期', '训练状态')
    },
    {
      title: '模型与配置',
      icon: Cpu,
      items: [
        { label: '训练配置', value: textValue(trainResultSource.value?.trainName) },
        { label: '所属模型', value: textValue(trainResultSource.value?.modelName) },
        rows.get('智能体'),
        rows.get('最佳模型')
      ].filter(Boolean)
    },
    {
      title: '数据范围',
      icon: DataLine,
      items: pick('区域名称', '客户名称', '所属行业名字', '训练数据量', '训练耗时')
    }
  ]
})
const trainResultErrorMessage = computed(() => {
  const result = selectedTrainResult.value || {}
  const message = textValue(result.errorMessage, result.error_message)
  return message === '-' ? '' : message
})
const resultMetricCards = computed(() => {
  const result = selectedTrainResult.value || {}
  return [
    { label: 'MAPE', value: metricValue(result.mape, '%'), hint: '越小越好', icon: TrendCharts, tone: 'tone-blue' },
    { label: 'WMAPE', value: metricValue(result.wmape, '%'), hint: '越小越好', icon: Histogram, tone: 'tone-green' },
    { label: 'SMAPE', value: metricValue(result.smape, '%'), hint: '越小越好', icon: PieChart, tone: 'tone-purple' },
    { label: 'RMSE', value: metricValue(result.rmse), hint: '越小越好', icon: Odometer, tone: 'tone-orange' },
    { label: 'MAE', value: metricValue(result.mae), hint: '越小越好', icon: DataLine, tone: 'tone-red' },
    { label: 'R²', value: metricValue(result.r2), hint: '越大越好', icon: Aim, tone: 'tone-teal' }
  ]
})
const asRecord = (value: unknown): Record<string, any> => {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, any>) : {}
}
const asArray = (value: unknown): Record<string, any>[] => {
  return Array.isArray(value) ? (value.filter((item) => item && typeof item === 'object') as Record<string, any>[]) : []
}
const JSON_ARRAY_PREVIEW_LIMIT = 100
const rawJsonTruncated = computed(() => {
  const hasLargeArray = (value: unknown): boolean => {
    if (Array.isArray(value)) {
      return value.length > JSON_ARRAY_PREVIEW_LIMIT || value.some(hasLargeArray)
    }
    return Boolean(
      value && typeof value === 'object' && Object.values(value as Record<string, unknown>).some(hasLargeArray)
    )
  }
  return (
    Boolean(selectedRequestJson.value.dataset_truncated) ||
    hasLargeArray(selectedTrainResult.value?.requestJson) ||
    hasLargeArray(selectedTrainResult.value?.resultJson)
  )
})
const jsonPreview = (value: unknown): unknown => {
  if (Array.isArray(value)) {
    if (value.length <= JSON_ARRAY_PREVIEW_LIMIT) return value.map(jsonPreview)
    return [
      ...value.slice(0, JSON_ARRAY_PREVIEW_LIMIT).map(jsonPreview),
      { __preview__: `已省略 ${value.length - JSON_ARRAY_PREVIEW_LIMIT} 条，共 ${value.length} 条` }
    ]
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, item]) => [key, jsonPreview(item)])
    )
  }
  return value
}
const stringifyJson = (value: unknown) => {
  if (!value) {
    return ''
  }
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  return JSON.stringify(jsonPreview(value), null, 2)
}
const selectedRequestJson = computed(() => asRecord(selectedTrainResult.value?.requestJson))
const selectedResultJson = computed(() => asRecord(selectedTrainResult.value?.resultJson))
const trainRequestJsonText = computed(() => stringifyJson(selectedTrainResult.value?.requestJson))
const trainResultJsonText = computed(() => stringifyJson(selectedTrainResult.value?.resultJson))
const isBatchFailed = (status: unknown) => String(status || '').toUpperCase() === 'FAILED'
const retrainBatch = async (batch: Record<string, any>) => {
  const retryBatchNo = String(batch.batchNo || batch.batch_no || '')
  if (!retryBatchNo || !trainResultSource.value?.trainCode || retrainingBatchNo.value) {
    return
  }
  retrainingBatchNo.value = retryBatchNo
  try {
    const result = await postJson('/model-train-execution/execute', {
      trainCode: trainResultSource.value?.trainCode,
      retryBatchNo
    })
    const data = (result as any).data || {}
    ElMessage.success(`已重新提交训练批次：${data.trainBatchNo || data.train_batch_no || '-'}`)
    if (trainResultSource.value) {
      await openTrainResult(trainResultSource.value)
      selectedTrainBatchNo.value = data.trainBatchNo || data.train_batch_no || selectedTrainBatchNo.value
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重新训练失败')
  } finally {
    retrainingBatchNo.value = ''
  }
}
const requestDatasetRows = computed(() => asArray(selectedRequestJson.value.dataset))
const pagedRequestDatasetRows = computed(() => {
  const start = (requestDatasetPage.value - 1) * requestDatasetSize.value
  return requestDatasetRows.value.slice(start, start + requestDatasetSize.value)
})
const requestDatasetColumns = computed(() => {
  const keys = new Set<string>()
  requestDatasetRows.value.slice(0, 20).forEach((row) => {
    Object.keys(row).forEach((key) => keys.add(key))
  })
  return Array.from(keys)
})
const resultMetricRows = computed(() => {
  const result = selectedResultJson.value
  const metrics = asRecord(result.metrics)
  const metadata = asRecord(result.metadata)
  const rows = Object.entries(metrics).map(([key, value]) => ({ label: key, value: value ?? '' }))
  ;[
    ['selected_model_name', result.selected_model_name],
    ['selection_reason', result.selection_reason],
    ['model_version', metadata.model_version],
    ['artifact_version', metadata.artifact_version],
    ['artifact_sha256', metadata.artifact_sha256],
    ['train_start', metadata.train_start],
    ['train_end', metadata.train_end],
    ['backtest_point_count', metadata.backtest_point_count],
    ['walk_forward_fold_count', metadata.walk_forward_fold_count]
  ].forEach(([label, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      rows.push({ label: String(label), value })
    }
  })
  return rows
})
const toNumber = (value: unknown) => {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}
const scoreErrorMetric = (value: unknown, values: Array<number | null>) => {
  const numberValue = toNumber(value)
  const availableValues = values.filter((item): item is number => item !== null)
  if (numberValue === null || !availableValues.length) return null
  const min = Math.min(...availableValues)
  const max = Math.max(...availableValues)
  if (min === max) return 100
  return ((max - numberValue) / (max - min)) * 100
}
const scoreHigherMetric = (value: unknown, values: Array<number | null>) => {
  const numberValue = toNumber(value)
  const availableValues = values.filter((item): item is number => item !== null)
  if (numberValue === null || !availableValues.length) return null
  const min = Math.min(...availableValues)
  const max = Math.max(...availableValues)
  if (min === max) return 100
  return ((numberValue - min) / (max - min)) * 100
}
const averageScore = (scores: Array<number | null>) => {
  const availableScores = scores.filter((item): item is number => item !== null)
  if (!availableScores.length) return 0
  return availableScores.reduce((sum, item) => sum + item, 0) / availableScores.length
}
const candidateRows = computed<Record<string, any>[]>(() => {
  const rows = asArray(selectedResultJson.value.candidate_evaluations)
  const metricRows: Record<string, any>[] = rows.map((row) => {
    const metrics = asRecord(row.metrics)
    return {
      ...row,
      selected: row.selected ? '是' : '否',
      mape: metrics.mape ?? '',
      wmape: metrics.wmape ?? '',
      rmse: metrics.rmse ?? '',
      mae: metrics.mae ?? '',
      r2: metrics.r2 ?? ''
    }
  })
  const rmseValues = metricRows.map((row) => toNumber(row.rmse))
  const mapeValues = metricRows.map((row) => toNumber(row.mape))
  const wmapeValues = metricRows.map((row) => toNumber(row.wmape))
  const maeValues = metricRows.map((row) => toNumber(row.mae))
  const r2Values = metricRows.map((row) => toNumber(row.r2))
  const scoredRows: Record<string, any>[] = metricRows.map((row) => {
    const score = averageScore([
      scoreErrorMetric(row.rmse, rmseValues),
      scoreErrorMetric(row.mape, mapeValues),
      scoreErrorMetric(row.wmape, wmapeValues),
      scoreErrorMetric(row.mae, maeValues),
      scoreHigherMetric(row.r2, r2Values)
    ])
    return { ...row, score: Number(score.toFixed(2)) }
  })
  return scoredRows
    .sort((a, b) => {
      if (a.selected !== b.selected) return a.selected === '是' ? -1 : 1
      if (b.score !== a.score) return b.score - a.score
      return (Number(a.rank) || Number.MAX_SAFE_INTEGER) - (Number(b.rank) || Number.MAX_SAFE_INTEGER)
    })
    .map((row, index) => ({ ...row, rank: index + 1 }))
})
const backtestRows = computed(() => asArray(selectedResultJson.value.rolling_backtest_results))
const issueRows = computed(() => asArray(selectedResultJson.value.issues))
const getChart = (el?: HTMLDivElement) => {
  return el ? echarts.getInstanceByDom(el) || echarts.init(el) : null
}
const renderBacktestChart = () => {
  const chart = getChart(backtestChartRef.value)
  if (!chart) return
  const labels = backtestRows.value.map((row) => row.stat_date || row.date || '-')
  const visiblePercent = labels.length > 18 ? Math.max(20, Math.round(1800 / labels.length)) : 100
  chart.setOption(
    {
      color: ['#2563eb', '#16a34a', '#f59e0b'],
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross', snap: true },
        formatter: (params: any) => {
          const items = Array.isArray(params) ? params : [params]
          const dataIndex = items[0]?.dataIndex ?? 0
          const row = backtestRows.value[dataIndex] || {}
          return [
            `<strong>${row.stat_date || row.date || '-'}</strong>`,
            `实际值：${row.actual_value ?? '-'}`,
            `预测值：${row.predicted_value ?? '-'}`,
            `绝对误差：${row.absolute_error ?? '-'}`,
            `误差率：${row.error_rate ?? '-'}`
          ].join('<br/>')
        }
      },
      legend: { top: 0 },
      grid: { left: 56, right: 56, top: 48, bottom: 82 },
      dataZoom: [
        { type: 'slider', xAxisIndex: 0, bottom: 24, height: 18, start: 0, end: visiblePercent },
        { type: 'inside', xAxisIndex: 0, start: 0, end: visiblePercent }
      ],
      xAxis: { type: 'category', data: labels, axisPointer: { show: true, snap: true } },
      yAxis: [
        { type: 'value', name: '气量' },
        { type: 'value', name: '误差' }
      ],
      series: [
        {
          name: '实际值',
          type: 'line',
          smooth: true,
          data: backtestRows.value.map((row) => toNumber(row.actual_value))
        },
        {
          name: '预测值',
          type: 'line',
          smooth: true,
          data: backtestRows.value.map((row) => toNumber(row.predicted_value))
        },
        {
          name: '绝对误差',
          type: 'bar',
          yAxisIndex: 1,
          data: backtestRows.value.map((row) => toNumber(row.absolute_error))
        }
      ]
    },
    true
  )
}
const renderVisibleResultChart = async () => {
  await nextTick()
  if (!trainResultVisible.value) return
  if (activeTrainResultTab.value === 'backtests' && backtestResultView.value === 'chart') {
    renderBacktestChart()
  }
}
const resizeResultCharts = () => {
  getChart(backtestChartRef.value)?.resize()
}
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  visibleFormFields.value.forEach((field) => {
    const fieldRules = []
    if (field.required) {
      fieldRules.push({
        required: true,
        message: `请输入${field.label}`,
        trigger: field.type === 'select' || field.type === 'radio' ? 'change' : 'blur'
      })
    }
    if (field.maxLength) {
      fieldRules.push({
        max: field.maxLength,
        message: `${field.label}不能超过${field.maxLength}个字符`,
        trigger: 'blur'
      })
    }
    if (field.pattern) {
      fieldRules.push({ pattern: new RegExp(field.pattern), message: `${field.label}格式不正确`, trigger: 'blur' })
    }
    if (fieldRules.length) {
      rules[field.prop] = fieldRules
    }
  })
  return rules
})

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
  fetcher: ({ page, size, keyword }) =>
    listPage(config.value.endpoint, {
      page,
      size,
      keyword: keyword || undefined,
      sortField: sortField.value || undefined,
      sortOrder: sortOrder.value || undefined,
      ...filterPayload.value
    })
})

const filterPayload = computed(() => {
  const payload = Object.fromEntries(
    Object.entries(filters)
      .map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value])
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
  config.value.filterFields?.forEach((field) => {
    if (field.type === 'dateRange') {
      const value = filters[field.prop]
      delete payload[field.prop]
      if (Array.isArray(value) && value.length === 2) {
        payload[field.startProp || `${field.prop}Start`] = value[0]
        payload[field.endProp || `${field.prop}End`] = value[1]
      }
    }
  })
  return payload
})

const featureDetailEntries = computed(() => {
  const detailProp = config.value.featureDetailProp
  if (!selectedRow.value || !detailProp) {
    return []
  }
  const details = selectedRow.value.featureDetails
  if (Array.isArray(details)) {
    return details
  }
  const values = selectedRow.value[detailProp] || {}
  return Object.entries(values).map(([key, value]) => ({ featureNo: key, featureCode: '-', featureValue: value }))
})

const resetForm = (row?: Record<string, any>) => {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, row ? { ...row } : { ...config.value.emptyForm })
}

const formatSourceOption = (row: Record<string, any>, field: BaseDataFieldConfig): Option => {
  const source = field.optionSource
  if (!source) {
    return { label: '', value: '' }
  }
  const value = row[source.valueProp]
  const code = row.configCode || row[source.valueProp]
  const label =
    source.labelTemplate === 'nameWithCode' ? `${row[source.labelProp] || '-'} (${code || '-'})` : row[source.labelProp]
  return { label, value, raw: row }
}

const loadFormOptions = async () => {
  const sourceFields = config.value.formFields.filter((field) => field.optionSource)
  await Promise.all(
    sourceFields.map(async (field) => {
      const source = field.optionSource
      if (!source) {
        return
      }
      try {
        const data = await listPage(source.endpoint, { page: 1, size: source.size || 1000 })
        formOptionMap[field.prop] = data.records.map((row) => formatSourceOption(row, field))
      } catch (error) {
        ElMessage.error(`${field.label}选项加载失败`)
        formOptionMap[field.prop] = []
      }
    })
  )
}

const handleFormOptionSelect = (field: BaseDataFieldConfig, option?: Option) => {
  if (config.value.trainExecution && field.prop === 'agentCode') {
    const selectedModel = (formOptionMap.modelId || []).find((item) => item.value === form.modelId)
    if (selectedModel && selectedModel.raw?.agentCode !== form.agentCode) {
      form.modelId = undefined
      form.modelCode = ''
      form.modelName = ''
    }
  }
  if (!field.fillProps) {
    return
  }
  Object.entries(field.fillProps).forEach(([targetProp, sourceProp]) => {
    form[targetProp] = option?.raw?.[sourceProp] || ''
  })
}

const resetFilters = () => {
  Object.keys(filters).forEach((key) => delete filters[key])
  config.value.filterFields?.forEach((field) => {
    filters[field.prop] = field.type === 'dateRange' ? [] : ''
  })
}

const resetAllSearch = () => {
  resetFilters()
  sortField.value = ''
  sortOrder.value = ''
  resetKeywordSearch()
}

const openFeatureDetail = (row: Record<string, any>) => {
  selectedRow.value = row
  featureDetailVisible.value = true
}

const openTrainResult = async (row: Record<string, any>) => {
  trainResultSource.value = row
  trainResultBatches.value = []
  selectedTrainBatchNo.value = ''
  trainBatchKeyword.value = ''
  trainBatchStatus.value = 'all'
  trainBatchPage.value = 1
  activeTrainResultTab.value = 'dataset'
  backtestResultView.value = 'chart'
  trainResultVisible.value = true
  trainResultLoading.value = true
  try {
    const result = await postJson('/model-train-execution/result', row)
    const data = (result as any).data || {}
    trainResultBatches.value = Array.isArray(data.batches) ? data.batches : []
    selectedTrainBatchNo.value = data.selectedBatchNo || trainResultBatches.value[0]?.batchNo || ''
    trainResultLoading.value = false
    if (selectedTrainBatchNo.value) {
      void loadTrainResultDetail(selectedTrainBatchNo.value)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '训练结果加载失败')
  } finally {
    trainResultLoading.value = false
  }
}

let trainResultDetailRequestId = 0
const loadTrainResultDetail = async (batchNo: string) => {
  if (!batchNo) return
  const current = trainResultBatches.value.find((item) => item.batchNo === batchNo)
  if (current?.requestJson != null || current?.resultJson != null) return
  const requestId = ++trainResultDetailRequestId
  trainResultDetailLoading.value = true
  try {
    const result = await postJson('/model-train-execution/result', { batchNo })
    if (requestId !== trainResultDetailRequestId) return
    const data = (result as any).data || {}
    const detail = data.batches?.[0]
    if (!detail) return
    const index = trainResultBatches.value.findIndex((item) => item.batchNo === batchNo)
    if (index >= 0) {
      trainResultBatches.value[index] = { ...trainResultBatches.value[index], ...detail }
    }
  } catch (error) {
    if (requestId === trainResultDetailRequestId) {
      ElMessage.error(error instanceof Error ? error.message : '训练批次详情加载失败')
    }
  } finally {
    if (requestId === trainResultDetailRequestId) {
      trainResultDetailLoading.value = false
    }
  }
}

const handleTrainResultSelect = (row?: Record<string, any>) => {
  selectedTrainBatchNo.value = row?.batchNo || ''
  requestDatasetPage.value = 1
  if (selectedTrainBatchNo.value) {
    void loadTrainResultDetail(selectedTrainBatchNo.value)
  }
}

const handleTrainBatchSizeChange = () => {
  trainBatchPage.value = 1
}

const handleSortChange = ({ prop, order }: { prop: string; order: string | null }) => {
  sortField.value = prop || ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  searchData()
}

const openCreate = () => {
  executionMode.value = false
  editingId.value = null
  resetForm()
  void loadFormOptions()
  if (config.value.trainExecution) void loadPlatformModels()
  dialogVisible.value = true
}

const openEdit = (row: Record<string, any>) => {
  executionMode.value = false
  editingId.value = row.id
  resetForm(row)
  void loadFormOptions()
  if (config.value.trainExecution) void loadPlatformModels()
  dialogVisible.value = true
}

const openExecute = (row: Record<string, any>) => {
  executionMode.value = true
  editingId.value = row.id
  resetForm(row)
  void loadFormOptions()
  void loadPlatformModels()
  dialogVisible.value = true
}

const saveData = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateRow(config.value.endpoint, { ...form, id: editingId.value })
    } else {
      await createRow(config.value.endpoint, form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

const persistCurrentForm = async () => {
  const payload = { ...form, id: editingId.value }
  const result = editingId.value
    ? await updateRow(config.value.endpoint, payload)
    : await createRow(config.value.endpoint, form)
  return (result as any).data || payload
}

const continueExecution = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  saving.value = true
  try {
    const saved = await persistCurrentForm()
    executionConfig.value = { ...form, ...saved }
    await postJson('/model-train-execution/validate', { trainCode: executionConfig.value.trainCode })
    ElMessage.success('训练数据校验通过')
    dialogVisible.value = false
    previewPage.value = 1
    await loadPreviewData()
    previewVisible.value = true
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '训练数据预览加载失败')
  } finally {
    saving.value = false
  }
}

const previewPayload = computed<PageRequest>(() => {
  const item = executionConfig.value || {}
  const payload: PageRequest = {
    page: previewPage.value,
    size: item.trainMode === 'RECENT' ? Math.max(Number(item.recentPeriods) || 36, 1) : previewSize.value,
    timeGranularity: item.timeGranularity || undefined,
    regionCode: item.regionCode || undefined,
    industryCode: item.industryCode || undefined,
    customerCode: item.customerCode || undefined,
    sortField: 'statDate',
    sortOrder: item.trainMode === 'RECENT' ? 'desc' : 'asc'
  }
  if (item.trainMode === 'RANGE') {
    payload.statDateStart = item.trainStartDate || undefined
    payload.statDateEnd = item.trainEndDate || undefined
  }
  return payload
})

const loadPreviewData = async () => {
  if (!executionConfig.value) {
    return
  }
  previewLoading.value = true
  try {
    const data = await listPage('/model-train-feature-data', previewPayload.value)
    previewRows.value = data.records
    previewTotal.value = executionConfig.value.trainMode === 'RECENT' ? data.records.length : data.total
  } finally {
    previewLoading.value = false
  }
}

const handlePreviewSizeChange = () => {
  previewPage.value = 1
  void loadPreviewData()
}

const executeTraining = async () => {
  if (executing.value) {
    return
  }
  if (!executionConfig.value?.trainCode) {
    ElMessage.error('训练配置编码不存在')
    return
  }
  executing.value = true
  try {
    await postJson('/model-train-execution/execute', { trainCode: executionConfig.value.trainCode })
    ElMessage.success('训练任务已提交')
    previewVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行训练失败')
  } finally {
    executing.value = false
  }
}

const granularityText = (value: unknown) => {
  if (value === 'DAY') return '日'
  if (value === 'TENDAY') return '旬'
  if (value === 'MONTH') return '月'
  return '-'
}

const loadPlatformModels = async () => {
  if (platformModels.value.length || platformModelsLoading.value) return
  platformModelsLoading.value = true
  try {
    const response = await getJson<PlatformModel[]>('/model-platform/models')
    platformModels.value = Array.isArray(response.data) ? response.data : []
  } catch (error) {
    platformModels.value = []
    ElMessage.error(error instanceof Error ? error.message : '模型数据要求加载失败')
  } finally {
    platformModelsLoading.value = false
  }
}

const platformFrequencyText = (value?: string) =>
  ({ day: '日', tenday: '旬', month: '月' })[value || ''] || value || '-'

const removeRow = async (row: Record<string, any>) => {
  await ElMessageBox.confirm('删除后不可恢复，确认删除这条数据？', '删除确认', { type: 'warning' })
  try {
    await deleteRow(config.value.endpoint, row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

watch(
  () => props.pageConfig,
  () => {
    keyword.value = ''
    page.value = 1
    resetFilters()
    resetForm()
    void loadFormOptions()
    void loadData()
  }
)

watch(trainBatchKeyword, () => {
  trainBatchPage.value = 1
})

watch(trainBatchStatus, () => {
  trainBatchPage.value = 1
})

watch(filteredTrainResultBatches, (rows) => {
  const maxPage = Math.max(1, Math.ceil(rows.length / trainBatchSize.value))
  if (trainBatchPage.value > maxPage) {
    trainBatchPage.value = maxPage
  }
})

watch(
  [trainResultVisible, activeTrainResultTab, backtestResultView, selectedTrainBatchNo, () => backtestRows.value.length],
  () => {
    void renderVisibleResultChart()
  }
)

onMounted(() => {
  resetFilters()
  resetForm()
  void loadFormOptions()
  void loadData()
  window.addEventListener('resize', resizeResultCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeResultCharts)
  getChart(backtestChartRef.value)?.dispose()
})

defineExpose({ loadData })
</script>

<style scoped>
.crud-page {
  display: flex;
  flex-direction: column;
  gap: var(--app-gap);
}

.search-input {
  width: 260px;
}

.filter-control {
  flex-shrink: 0;
}

.dialog-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
  row-gap: 2px;
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.dialog-form :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: #344054;
  font-weight: 600;
}

.form-item-full {
  grid-column: 1 / -1;
}

.field-helper {
  width: 100%;
  margin: 6px 0 0;
  color: #8492a6;
  font-size: 12px;
  line-height: 18px;
}

.feature-dialog-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.feature-dialog-icon {
  display: grid;
  flex: 0 0 56px;
  width: 56px;
  height: 56px;
  place-items: center;
  border-radius: 50%;
  color: var(--app-primary);
  background: #eef7ff;
  font-size: 26px;
}

.feature-dialog-header p {
  margin: 0 0 3px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
}

.feature-dialog-header h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
  line-height: 1.35;
}

.feature-dialog-header span {
  display: block;
  margin-top: 3px;
  color: #8492a6;
  font-size: 12px;
}

:global(.feature-definition-dialog .el-dialog__body) {
  padding-top: 22px;
  background: linear-gradient(180deg, #fbfdff 0%, #ffffff 100%);
}

:global(.model-training-dialog .el-dialog__body) {
  padding-top: 22px;
  background: linear-gradient(180deg, #fbfdff 0%, #ffffff 100%);
}

:global(.model-training-dialog .el-select__selected-item) {
  max-width: 100%;
}

:global(.model-training-dialog .el-textarea__inner) {
  min-height: 82px !important;
  resize: vertical;
}

:global(.feature-definition-dialog .el-textarea__inner) {
  min-height: 90px !important;
  resize: vertical;
}

.dialog-form :deep(.el-input__wrapper),
.dialog-form :deep(.el-select__wrapper),
.dialog-form :deep(.el-input-number) {
  min-height: var(--app-control-height);
}

.dialog-form :deep(.el-date-editor.el-input),
.dialog-form :deep(.el-date-editor.el-input__wrapper) {
  width: 100%;
}

.column-header-with-tip {
  cursor: help;
  text-decoration: underline dotted;
  text-underline-offset: 3px;
}

.feature-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #475467;
  font-size: 13px;
}

.feature-summary span {
  border: 1px solid #eaecf0;
  border-radius: 6px;
  padding: 4px 8px;
}

.feature-detail :deep(.el-table .cell) {
  font-family: inherit;
  font-size: 12px;
}

.preview-summary,
.preview-scope {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.preview-summary span,
.preview-scope span {
  border: 1px solid #eaecf0;
  border-radius: 6px;
  padding: 5px 9px;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
}

.preview-scope span {
  color: #475467;
  font-weight: 600;
}

.preview-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.train-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 260px;
}

:global(.train-result-dialog .el-dialog__header) {
  position: relative;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  min-height: 64px;
  margin-bottom: 0;
  padding: 12px 56px 12px 20px;
  border-bottom: 1px solid #dce6f2;
  background: linear-gradient(110deg, var(--app-primary-soft) 0%, #f3f7fc 68%, #edf3fa 100%);
}

:global(.train-result-dialog .el-dialog__headerbtn) {
  position: absolute;
  top: 50%;
  right: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: 0;
  border-radius: 6px;
  color: #475467;
  transform: translateY(-50%);
}

:global(.train-result-dialog .el-dialog__headerbtn:hover) {
  background: rgba(22, 119, 255, 0.08);
  color: var(--app-primary);
}

.train-result-heading h2,
.train-result-heading p {
  margin: 0;
}

.train-result-heading h2 {
  color: #101828;
  font-size: 20px;
  font-weight: 700;
  line-height: 28px;
}

.train-result-heading p {
  margin-top: 3px;
  color: #667085;
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
}

:global(.train-result-dialog .el-dialog__body) {
  box-sizing: border-box;
  height: calc(100vh - 64px);
  padding: 0;
  overflow: hidden;
  background: #f6f8fb;
}

.train-result-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  min-height: calc(100vh - 64px);
}

.train-result-sidebar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  overflow: auto;
  border-right: 1px solid #e5e7eb;
  background: #f8fafc;
}

.result-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #344054;
  font-size: 15px;
  font-weight: 600;
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
  font-weight: 500;
  cursor: pointer;
}

.result-sidebar-tabs button.active {
  color: #2563eb;
  font-weight: 600;
}

.result-sidebar-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  border-radius: 999px;
  background: #2563eb;
  content: '';
}

.result-batch-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 12px;
  border: 1px solid #e5e7eb;
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

.train-batch-list {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

.result-batch-card:hover,
.result-batch-card.active {
  border-color: #409eff;
  background: #eff6ff;
  box-shadow: 0 8px 20px rgba(16, 24, 40, 0.08);
}

.batch-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.batch-card-top strong {
  min-width: 0;
  overflow: hidden;
  color: #182230;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-batch-card small {
  color: #667085;
  font-size: 12px;
  font-weight: 400;
}

.result-batch-card > span:not(.batch-card-top) {
  color: #475467;
  font-size: 13px;
  font-weight: 500;
}

.result-batch-card > .el-button {
  align-self: flex-end;
  height: auto;
  padding: 0;
  font-size: 12px;
}

.train-status-tag {
  border: 0 !important;
  border-radius: 4px !important;
  font-weight: 600;
}

.train-status-tag.status-running {
  background: #dbeafe !important;
  color: #3b82f6 !important;
}

.train-status-tag.status-pending {
  background: #fef3c7 !important;
  color: #d97706 !important;
}

.train-status-tag.status-success {
  background: #dcfce7 !important;
  color: #22b85b !important;
}

.train-status-tag.status-failed {
  background: #fee2e2 !important;
  color: #ef4444 !important;
}

.result-sidebar-empty {
  padding: 20px 0;
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

.train-result-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  background: #f4f7fb;
}

.result-hero,
.result-info-panel,
.result-metrics-panel,
.result-tabs {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(16, 24, 40, 0.04);
}

.result-hero {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 16px;
  min-height: 58px;
  padding: 0 2px;
  border: 0;
  background: transparent;
  box-shadow: none;
}

.result-hero-content {
  min-width: 0;
  flex: 1;
}

.result-hero-actions {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
}

.result-hero-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  border-radius: 12px;
  background: linear-gradient(135deg, #dbeafe, #eff6ff);
  color: #2563eb;
  font-size: 22px;
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.16);
}

.result-hero-title {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #101828;
  font-size: 20px;
}

.result-hero-title strong {
  font-weight: 700;
}

.result-hero-subtitle {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  color: #475467;
  font-size: 13px;
  font-weight: 400;
}

.result-hero-subtitle span:first-child {
  color: #344054;
  font-weight: 600;
}

.train-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.train-summary-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 3px 10px rgba(31, 64, 104, 0.05);
}

.train-summary-card h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 11px 14px;
  border-bottom: 1px solid #e8eef6;
  background: linear-gradient(90deg, #edf5ff 0%, #f8fbff 72%, #fff 100%);
  color: #182230;
  font-size: 14px;
  font-weight: 700;
}

.train-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  color: #27364d;
  font-size: 14px;
  font-weight: 700;
}

.train-summary-card h4 > span,
.train-section-title > span {
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

.train-summary-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
  padding: 14px;
}

.train-summary-item {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.train-summary-item > span {
  flex: 0 0 auto;
  color: #667085;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.train-summary-item > span::after {
  color: #98a2b3;
  content: '：';
}

.train-summary-item > strong {
  min-width: 0;
  overflow: hidden;
  color: #101828;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.train-summary-item > strong.status-success {
  color: #22b85b;
}

.train-summary-item > strong.status-failed {
  color: #ef4444;
}

.train-summary-item > strong.status-running {
  color: #3b82f6;
}

.train-summary-item > strong.status-pending {
  color: #d97706;
}

.result-info-panel {
  display: flex;
  flex-direction: column;
}

.result-info-title {
  display: flex;
  align-items: center;
  padding: 14px 16px 0;
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
}

.result-info-title span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.result-info-title .el-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: #dbeafe;
  color: #2563eb;
  font-size: 16px;
}

.result-overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: 32px;
  row-gap: 10px;
  padding: 16px 20px 18px;
}

.result-overview-item {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  align-items: baseline;
  column-gap: 12px;
  min-width: 0;
}

.result-overview-item span,
.result-metric-card span {
  color: #667085;
  font-size: 12px;
  font-weight: 400;
}

.result-overview-item strong {
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-overview-item strong.status-success {
  color: #22b85b;
}

.result-overview-item strong.status-failed {
  color: #ef4444;
}

.result-overview-item strong.status-running {
  color: #3b82f6;
}

.result-overview-item strong.status-pending {
  color: #d97706;
}

.result-metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.result-metrics-panel {
  padding: 14px;
}

.result-metric-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 82px;
  padding: 12px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.metric-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-card-head .el-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  font-size: 16px;
}

.result-metric-card strong {
  color: #1d4ed8;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
}

.result-metric-card small {
  color: #667085;
  font-size: 12px;
  font-weight: 400;
}

.result-metric-card.tone-blue {
  border-color: #bfdbfe;
  background: linear-gradient(180deg, #ffffff 0%, #eff6ff 100%);
}

.result-metric-card.tone-blue .metric-card-head .el-icon {
  background: #dbeafe;
  color: #2563eb;
}

.result-metric-card.tone-blue strong {
  color: #1d4ed8;
}

.result-metric-card.tone-green {
  border-color: #bbf7d0;
  background: linear-gradient(180deg, #ffffff 0%, #f0fdf4 100%);
}

.result-metric-card.tone-green .metric-card-head .el-icon {
  background: #dcfce7;
  color: #16a34a;
}

.result-metric-card.tone-green strong {
  color: #15803d;
}

.result-metric-card.tone-purple {
  border-color: #e9d5ff;
  background: linear-gradient(180deg, #ffffff 0%, #faf5ff 100%);
}

.result-metric-card.tone-purple .metric-card-head .el-icon {
  background: #f3e8ff;
  color: #7c3aed;
}

.result-metric-card.tone-purple strong {
  color: #6d28d9;
}

.result-metric-card.tone-orange {
  border-color: #fed7aa;
  background: linear-gradient(180deg, #ffffff 0%, #fff7ed 100%);
}

.result-metric-card.tone-orange .metric-card-head .el-icon {
  background: #ffedd5;
  color: #ea580c;
}

.result-metric-card.tone-orange strong {
  color: #c2410c;
}

.result-metric-card.tone-red {
  border-color: #fecdd3;
  background: linear-gradient(180deg, #ffffff 0%, #fff1f2 100%);
}

.result-metric-card.tone-red .metric-card-head .el-icon {
  background: #ffe4e6;
  color: #e11d48;
}

.result-metric-card.tone-red strong {
  color: #be123c;
}

.result-metric-card.tone-teal {
  border-color: #99f6e4;
  background: linear-gradient(180deg, #ffffff 0%, #f0fdfa 100%);
}

.result-metric-card.tone-teal .metric-card-head .el-icon {
  background: #ccfbf1;
  color: #0f766e;
}

.result-metric-card.tone-teal strong {
  color: #0f766e;
}

.result-tabs {
  padding: 0 14px 14px;
  min-height: 220px;
}

.result-tabs :deep(.el-tabs__item) {
  color: #475467;
  font-size: 13px;
  font-weight: 500;
}

.result-tabs :deep(.el-tabs__item.is-active) {
  color: var(--app-primary);
  font-weight: 600;
}

.result-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.result-tab-label .el-icon {
  font-size: 14px;
}

.result-tabs :deep(.el-table th.el-table__cell) {
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

.result-tabs :deep(.el-table td.el-table__cell) {
  color: #475467;
  font-size: 13px;
  font-weight: 400;
}

.result-view-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.result-chart {
  width: 100%;
  height: 340px;
  border: 1px solid #eaecf0;
  border-radius: 6px;
  background: #fff;
}

.json-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.result-subtitle {
  margin-bottom: 8px;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
}

.result-json {
  max-height: 300px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid #eaecf0;
  border-radius: 6px;
  background: #f8fafc;
  color: #344054;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.training-requirement-card {
  margin-top: 14px;
  padding: 14px 16px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: linear-gradient(135deg, #eff6ff 0%, #f8fbff 100%);
}

.requirement-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #1e3a5f;
  font-weight: 700;
}
.training-requirement-card > p {
  margin: 10px 0 12px;
  color: #52657d;
  font-size: 13px;
  line-height: 1.6;
}
.requirement-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.requirement-grid > span {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 10px 12px;
  border: 1px solid #e1ebf7;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.82);
}
.requirement-grid small {
  color: #8492a6;
}
.requirement-grid strong {
  color: #243b5a;
  font-size: 13px;
}

@media (max-width: 760px) {
  .search-input {
    width: 100%;
  }

  .filter-control {
    width: 100% !important;
  }

  .dialog-form {
    grid-template-columns: 1fr;
  }

  .train-result-layout {
    grid-template-columns: 1fr;
  }

  .train-result-sidebar {
    max-height: 260px;
    border-right: 0;
    border-bottom: 1px solid #e5e7eb;
  }

  .result-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .train-summary-grid,
  .result-overview-grid,
  .result-metric-grid {
    grid-template-columns: 1fr;
  }

  .result-overview-item {
    grid-template-columns: 96px minmax(0, 1fr);
  }

  .json-grid {
    grid-template-columns: 1fr;
  }

  .requirement-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
