<template>
  <div class="agent-detail" :class="{ dragging: isDragging }" :style="{ '--split': splitRatio }">
    <!-- 左侧工作区 -->
    <div class="workspace" :style="{ flexBasis: splitRatio * 100 + '%' }">
      <nav class="agent-workspace-tabs" aria-label="智能体列表">
        <RouterLink
          v-for="item in agentOptions"
          :key="item.id"
          :to="item.path"
          :class="{ active: item.id === agentId }"
        >
          <span class="agent-tab-icon" :class="item.id">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path :d="agentTabIcon(item.id)" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" />
            </svg>
          </span>
          <span><strong>{{ item.name }}</strong><small>{{ agentTabDescription(item.id) }}</small></span>
        </RouterLink>
      </nav>
      <div class="workspace-scroll">
        <!-- ==================== -->
        <!-- 月度销量预测专用布局 -->
        <!-- ==================== -->
        <template v-if="isMonthlySales">
          <!-- 01. 数据选择 -->
          <section class="section-block">
            <div class="section-head">
              <span class="section-badge">01</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title">
                    <el-icon><DataAnalysis /></el-icon>数据选择
                  </h2>
                  <p class="section-hint">配置数据来源与筛选条件</p>
                </div>
                <button class="refresh-result-btn" :disabled="loadingResult" @click="refreshLatestResult">
                  <svg v-if="!loadingResult" viewBox="0 0 20 20" fill="none">
                    <path
                      d="M16.2 6.5A7 7 0 1 0 17 10"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    />
                    <path
                      d="M16.5 3.5V6.8H13.2"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                  <span v-else class="mini-spinner"></span>
                  获取最新预测结果
                </button>
              </div>
            </div>
            <div class="form-grid">
              <div class="form-field">
                <label>数据来源</label>
                <el-select v-model="config.dataSource" placeholder="请选择" class="field-input" disabled>
                  <el-option label="历史用气数据" value="history" />
                </el-select>
              </div>
              <div class="form-field">
                <label>时间范围</label>
                <el-date-picker
                  v-model="config.dateRange"
                  type="daterange"
                  range-separator="-"
                  start-placeholder="开始"
                  end-placeholder="结束"
                  class="field-input"
                  disabled
                />
              </div>
              <div class="form-field full">
                <label>区域选择</label>
                <div class="region-selector">
                  <button
                    v-for="r in monthlyRegions"
                    :key="r.value"
                    class="region-btn"
                    :class="{ active: selectedProvince === r.value }"
                    @click="switchProvince(r.value)"
                  >
                    {{ r.label }}
                  </button>
                </div>
              </div>
            </div>
          </section>

          <!-- 02. 行业选择 -->
          <section class="section-block">
            <div class="section-head">
              <span class="section-badge">02</span>
              <div>
                <h2 class="section-title">
                  <el-icon><Grid /></el-icon>行业选择
                </h2>
                <p class="section-hint">选择行业查看预测结果</p>
              </div>
            </div>
            <div class="industry-select-wrap">
              <div class="industry-select-current" v-if="currentResult">
                <div class="industry-icon" :style="{ background: industryColors[selectedIndustry] }">
                  {{ selectedIndustry.charAt(0) }}
                </div>
                <div class="industry-select-info">
                  <span class="industry-select-name">{{ selectedIndustry }}</span>
                  <span class="industry-select-mape">MAPE {{ currentResult.metrics.mape }}%</span>
                </div>
              </div>
              <el-select
                v-model="selectedIndustry"
                placeholder="选择行业"
                class="industry-select"
                @change="switchIndustry(selectedIndustry)"
              >
                <el-option v-for="ind in industries" :key="ind" :value="ind" :label="ind">
                  <div class="industry-option">
                    <span class="industry-option-icon" :style="{ background: industryColors[ind] }">{{
                      ind.charAt(0)
                    }}</span>
                    <span class="industry-option-name">{{ ind }}</span>
                    <span class="industry-option-mape" v-if="provinceMape[ind] !== undefined"
                      >MAPE {{ provinceMape[ind] }}%</span
                    >
                  </div>
                </el-option>
              </el-select>
            </div>
          </section>

          <!-- 03. 模型信息 -->
          <section class="section-block" v-if="currentResult">
            <div class="section-head">
              <span class="section-badge">03</span>
              <div>
                <h2 class="section-title">
                  <el-icon><Cpu /></el-icon>推荐模型
                </h2>
                <p class="section-hint">基于回测精度自动推荐最优模型</p>
              </div>
            </div>
            <div class="model-info-card">
              <div class="model-info-header">
                <div class="model-info-name">
                  <el-icon><MagicStick /></el-icon>{{ currentResult.model_name }}
                </div>
                <div class="model-info-tag">最佳模型</div>
              </div>
              <div class="metrics-grid">
                <div class="metric-item">
                  <el-icon class="metric-icon"><TrendCharts /></el-icon>
                  <span class="metric-label">MAPE</span>
                  <span class="metric-value" :class="mapeClass(currentResult.metrics.mape)"
                    >{{ formatShortTermMetric(currentResult.metrics.mape, '%') }}</span
                  >
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><DataLine /></el-icon>
                  <span class="metric-label">MAE</span>
                  <span class="metric-value">{{ formatShortTermMetric(currentResult.metrics.mae) }}</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><Histogram /></el-icon>
                  <span class="metric-label">RMSE</span>
                  <span class="metric-value">{{ formatShortTermMetric(currentResult.metrics.rmse) }}</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><Finished /></el-icon>
                  <span class="metric-label">R²</span>
                  <span class="metric-value">{{ formatShortTermMetric(currentResult.metrics.r2) }}</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 04. 可视化输出 -->
          <section class="section-block" v-if="currentResult">
            <div class="section-head">
              <span class="section-badge">04</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title">
                    <el-icon><PieChart /></el-icon>可视化输出
                  </h2>
                  <p class="section-hint">实际值 vs 预测值对比</p>
                </div>
                <div class="chart-toggle">
                  <button
                    v-for="t in chartTypes"
                    :key="t.key"
                    class="toggle-btn"
                    :class="{ active: chartType === t.key }"
                    @click="chartType = t.key"
                  >
                    {{ t.label }}
                  </button>
                </div>
              </div>
            </div>
            <div class="chart-box">
              <div class="chart-label">滚动回测结果（实际值 vs 预测值）</div>
              <div ref="chartRefTop" class="chart" style="height: 300px"></div>
              <div class="chart-label" v-if="currentResult.future_dates">历史与近期预测</div>
              <div ref="chartRefBottom" class="chart" style="height: 300px" v-if="currentResult.future_dates"></div>
              <el-empty v-else description="当前范围暂无成功的近期预测结果" :image-size="72" />
            </div>
            <div class="data-summary" v-if="currentResult.future_dates">
              <div class="summary-item">
                <span class="summary-label">回测数据点</span>
                <span class="summary-value">{{ currentResult.dates.length }} 月</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">未来预测</span>
                <span class="summary-value">{{ currentResult.future_dates.length }} 月</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">预测起始</span>
                <span class="summary-value">{{ currentResult.future_dates[0] }}</span>
              </div>
            </div>
          </section>

          <!-- 加载中 -->
          <section class="section-block" v-if="loadingResult">
            <div class="loading-area">
              <div class="loading-spinner"></div>
              <span class="loading-text">加载预测结果...</span>
            </div>
          </section>
        </template>

        <!-- ==================== -->
        <!-- 短期客户预测专用布局 -->
        <!-- ==================== -->
        <template v-else-if="isShortTerm">
          <header class="short-term-hero">
            <div>
              <h1>短期客户预测</h1>
              <p>基于历史用气数据、运行特征与天气因素，预测客户短期用气趋势</p>
            </div>
          </header>

          <section class="section-block compact-section">
            <div class="section-head">
              <span class="section-badge">01</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title"><el-icon><DataAnalysis /></el-icon>选择分析范围</h2>
                  <p class="section-hint">默认选中一个客户，点击预测后发起客户级预测</p>
                </div>
              </div>
            </div>
            <div class="short-term-filter-grid">
              <div class="form-field">
                <label>地区</label>
                <el-select v-model="selectedStProvince" @change="switchStProvince(selectedStProvince)">
                  <el-option v-for="province in stProvinces" :key="province" :label="province" :value="province" />
                </el-select>
              </div>
              <div class="form-field">
                <label>行业</label>
                <el-select v-model="selectedStIndustry" @change="switchStIndustry(selectedStIndustry)">
                  <el-option v-for="industry in stIndustries" :key="industry" :label="industry" :value="industry" />
                </el-select>
              </div>
              <div class="form-field">
                <label>客户</label>
                <el-select
                  v-model="selectedStCustomer"
                  filterable
                  placeholder="请选择客户"
                  @change="switchStCustomer(selectedStCustomer)"
                >
                  <el-option v-for="customer in stCustomers" :key="customer" :label="customer" :value="customer" />
                </el-select>
              </div>
              <div class="short-term-filter-actions">
                <el-button @click="resetShortTermFilters">重置</el-button>
                <button
                  class="refresh-result-btn predict-result-btn"
                  :disabled="loadingResult || runningForecast || isStreaming || !selectedStCustomer"
                  @click="runShortTermForecast"
                >
                  <svg v-if="!runningForecast" viewBox="0 0 20 20" fill="currentColor">
                    <path
                      d="M7.6 4.3c-.9-.6-2.1.1-2.1 1.2v9c0 1.1 1.2 1.8 2.1 1.2l7-4.5c.8-.5.8-1.8 0-2.3l-7-4.6Z"
                    />
                  </svg>
                  <span v-else class="mini-spinner"></span>
                  预测
                </button>
              </div>
            </div>
          </section>

          <section v-if="!loadingResult && !currentResult" class="section-block compact-section">
            <el-empty :description="shortTermEmptyDescription" :image-size="72" />
          </section>

          <section v-if="currentResult" class="section-block compact-section">
            <div class="section-head">
              <span class="section-badge">02</span>
              <div>
                <h2 class="section-title"><el-icon><Cpu /></el-icon>推荐模型</h2>
                <p class="section-hint">基于回测精度自动推荐最优模型</p>
              </div>
            </div>
            <div class="model-info-card short-term-model-card">
              <div class="model-info-header">
                <div class="model-info-name">{{ currentResult.bestModel || currentResult.model_name || '-' }}</div>
                <div class="model-info-tag">最佳模型</div>
              </div>
              <div class="metrics-grid">
                <div class="metric-item">
                  <span class="metric-label compact-metric-label"><el-icon class="metric-icon"><TrendCharts /></el-icon>MAPE</span>
                  <span class="metric-value" :class="mapeClass(currentResult.metrics.mape)"
                    >{{ formatShortTermMetric(currentResult.metrics.mape, '%') }}</span
                  >
                </div>
                <div class="metric-item">
                  <span class="metric-label compact-metric-label"><el-icon class="metric-icon"><DataLine /></el-icon>MAE</span>
                  <span class="metric-value">{{ formatShortTermMetric(currentResult.metrics.mae) }}</span>
                </div>
                <div class="metric-item">
                  <span class="metric-label compact-metric-label"><el-icon class="metric-icon"><Histogram /></el-icon>RMSE</span>
                  <span class="metric-value">{{ formatShortTermMetric(currentResult.metrics.rmse) }}</span>
                </div>
                <div class="metric-item">
                  <span class="metric-label compact-metric-label"><el-icon class="metric-icon"><Finished /></el-icon>R²</span>
                  <span class="metric-value">{{ formatShortTermMetric(currentResult.metrics.r2) }}</span>
                </div>
              </div>
            </div>
          </section>

          <section v-if="currentResult" class="section-block compact-section">
            <div class="section-head">
              <span class="section-badge">03</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title"><el-icon><TrendCharts /></el-icon>模型评估（滚动回测结果）</h2>
                  <p class="section-hint">实际值与预测值对比及模型精度</p>
                </div>
                <div class="chart-toggle">
                  <button
                    v-for="type in chartTypes"
                    :key="type.key"
                    class="toggle-btn"
                    :class="{ active: chartType === type.key }"
                    @click="chartType = type.key"
                  >
                    {{ type.label }}
                  </button>
                </div>
              </div>
            </div>
            <div class="result-scope-bar">
              <span>评估范围</span>
              <strong>{{ selectedStProvince }} / {{ selectedStIndustry }} / {{ selectedStCustomer || '-' }}</strong>
              <em>客户独立训练模型</em>
            </div>
            <div class="short-term-evaluation">
              <section class="result-chart-panel compact-chart-panel">
                <ForecastComparisonChart
                  v-if="currentResult.dates?.length"
                  class="chart"
                  :dates="currentResult.dates"
                  :reference-values="currentResult.actual || []"
                  :forecast-values="currentResult.predicted || []"
                  :chart-type="chartType === 'bar' ? 'bar' : 'line'"
                  reference-name="实际值"
                  forecast-name="预测值"
                  reference-color="#10b981"
                  forecast-color="#3b82f6"
                />
                <el-empty v-else description="暂无滚动回测数据" :image-size="64" />
              </section>
            </div>
          </section>

          <section v-if="currentResult" class="section-block compact-section">
            <div class="section-head">
              <span class="section-badge">04</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title"><el-icon><Histogram /></el-icon>未来预测结果</h2>
                  <p class="section-hint">去年同期、最新预测及每日预测明细</p>
                </div>
                <div class="chart-toggle">
                  <button
                    v-for="type in futureChartTypes"
                    :key="type.key"
                    class="toggle-btn"
                    :class="{ active: futureChartType === type.key }"
                    @click="futureChartType = type.key"
                  >
                    {{ type.label }}
                  </button>
                </div>
              </div>
            </div>
            <div class="result-scope-bar">
              <span>预测对象</span>
              <strong>{{ selectedStProvince }} / {{ selectedStIndustry }} / {{ selectedStCustomer || '-' }}</strong>
              <em>客户级预测结果</em>
            </div>
            <div v-if="shortTermComparablePairs.length" class="future-comparison-summary">
              <div class="reference-total">
                <span class="comparison-icon"><el-icon><DataAnalysis /></el-icon></span>
                <div><span>去年同期总量</span><strong>{{ shortTermLastYearTotal }}</strong><small>{{ shortTermLastYearStats }}</small></div>
              </div>
              <div class="forecast-total">
                <span class="comparison-icon"><el-icon><Histogram /></el-icon></span>
                <div><span>本次预测总量</span><strong>{{ shortTermForecastTotal }}</strong><small>{{ shortTermForecastStats }}</small></div>
              </div>
              <div :class="`trend-${shortTermTrendTone}`">
                <span class="comparison-icon"><el-icon><TrendCharts /></el-icon></span>
                <div><span>同比变化</span><strong>{{ shortTermTrendText }}</strong><small>{{ shortTermTrendDescription }}</small></div>
              </div>
            </div>
            <div class="short-term-future-grid">
              <section v-if="futureChartType !== 'table'" class="result-chart-panel compact-chart-panel">
                <ForecastComparisonChart v-if="currentResult.future_dates?.length" class="chart" :dates="currentResult.future_dates" :reference-values="currentResult.last_year_values || []" :forecast-values="currentResult.future_predicted || []" :chart-type="futureChartType === 'line' ? 'line' : 'bar'" />
                <el-empty v-else description="暂无未来预测数据" :image-size="64" />
              </section>
              <div v-else class="future-table-wrap">
                <el-table :data="shortTermFutureRows" size="small" height="280">
                  <el-table-column prop="date" label="日期" min-width="108" align="center" class-name="nowrap-cell" />
                  <el-table-column prop="samePeriodValue" label="去年同期" min-width="86" align="center" />
                  <el-table-column prop="value" label="最新预测" min-width="86" align="center" />
                  <el-table-column prop="yearOnYearIncreaseText" label="同比增量" min-width="100" align="center">
                    <template #default="{ row }">
                      <span :class="row.yearOnYearTone">{{ row.yearOnYearIncreaseText }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="yearOnYearGrowthRateText" label="同比增幅" min-width="100" align="center">
                    <template #default="{ row }">
                      <span :class="row.yearOnYearTone">{{ row.yearOnYearGrowthRateText }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
            <div class="forecast-meta-info">
              <span>预测起始时间：{{ shortTermForecastStart }}</span>
              <span>预测天数：{{ shortTermFutureValues.length }} 天</span>
            </div>
            <div v-if="currentResult.analysisContent" class="forecast-analysis">
              <strong>AI 预测解析</strong>
              <p>{{ currentResult.analysisContent }}</p>
            </div>
          </section>

          <section class="section-block" v-if="loadingResult">
            <div class="loading-area">
              <div class="loading-spinner"></div>
              <span class="loading-text">加载预测结果...</span>
            </div>
          </section>
        </template>

        <!-- ==================== -->
        <!-- 冬供保供预测专用布局 -->
        <!-- ==================== -->
        <template v-else-if="isWinterSupply">
          <!-- 01. 区域选择 -->
          <section class="section-block">
            <div class="section-head">
              <span class="section-badge">01</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title">
                    <el-icon><Location /></el-icon>区域选择
                  </h2>
                  <p class="section-hint">选择省份查看冬供预测结果</p>
                </div>
                <button class="refresh-result-btn" :disabled="loadingResult" @click="refreshLatestResult">
                  <svg v-if="!loadingResult" viewBox="0 0 20 20" fill="none">
                    <path
                      d="M16.2 6.5A7 7 0 1 0 17 10"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    />
                    <path
                      d="M16.5 3.5V6.8H13.2"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                  <span v-else class="mini-spinner"></span>
                  获取最新预测结果
                </button>
              </div>
            </div>
            <div class="region-selector">
              <button
                v-for="p in wsProvinces"
                :key="p"
                class="region-btn"
                :class="{ active: selectedWsProvince === p }"
                @click="switchWsProvince(p)"
              >
                {{ p }}
                <span v-if="wsProvinceMape[p] !== undefined" class="region-mape">MAPE {{ wsProvinceMape[p] }}%</span>
              </button>
            </div>
          </section>

          <!-- 02. 模型信息 -->
          <section class="section-block" v-if="currentResult">
            <div class="section-head">
              <span class="section-badge">02</span>
              <div>
                <h2 class="section-title">
                  <el-icon><Cpu /></el-icon>推荐模型
                </h2>
                <p class="section-hint">基于回测精度自动推荐最优模型</p>
              </div>
            </div>
            <div class="model-info-card">
              <div class="model-info-header">
                <div>
                  <div class="model-info-name">
                    <el-icon><MagicStick /></el-icon>{{ currentResult.model_name }}
                  </div>
                  <div class="model-info-desc" v-if="currentResult.formula">{{ currentResult.formula }}</div>
                </div>
                <div class="model-info-tag">最佳模型</div>
              </div>
              <div class="metrics-grid">
                <div class="metric-item">
                  <el-icon class="metric-icon"><TrendCharts /></el-icon>
                  <span class="metric-label">MAPE</span>
                  <span class="metric-value" :class="mapeClass(currentResult.metrics.mape)"
                    >{{ currentResult.metrics.mape }}%</span
                  >
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><Finished /></el-icon>
                  <span class="metric-label">R²</span>
                  <span class="metric-value">{{ currentResult.metrics.r2 ?? '-' }}</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><Histogram /></el-icon>
                  <span class="metric-label">RMSE</span>
                  <span class="metric-value">{{ currentResult.metrics.rmse }}</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><DataLine /></el-icon>
                  <span class="metric-label">MAE</span>
                  <span class="metric-value">{{ currentResult.metrics.mae }}</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 03. 可视化输出 -->
          <section class="section-block" v-if="currentResult">
            <div class="section-head">
              <span class="section-badge">03</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title">
                    <el-icon><PieChart /></el-icon>可视化输出
                  </h2>
                  <p class="section-hint">实际值 vs 预测值对比（旬度）</p>
                </div>
                <div class="chart-toggle">
                  <button
                    v-for="t in chartTypes"
                    :key="t.key"
                    class="toggle-btn"
                    :class="{ active: chartType === t.key }"
                    @click="chartType = t.key"
                  >
                    {{ t.label }}
                  </button>
                </div>
              </div>
            </div>
            <div class="chart-box">
              <div class="chart-label" v-if="currentResult.dates">滚动回测结果（实际值 vs 预测值）</div>
              <div ref="chartRefTop" class="chart" style="height: 300px" v-if="currentResult.dates"></div>
              <div class="chart-label" v-if="currentResult.future_dates">历史与未来预测</div>
              <div ref="chartRefBottom" class="chart" style="height: 300px" v-if="currentResult.future_dates"></div>
            </div>
            <div class="data-summary" v-if="currentResult.future_dates">
              <div class="summary-item" v-if="currentResult.dates">
                <span class="summary-label">回测数据点</span>
                <span class="summary-value">{{ currentResult.dates.length }} 旬</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">未来预测</span>
                <span class="summary-value">{{ currentResult.future_dates.length }} 旬</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">预测起始</span>
                <span class="summary-value">{{ currentResult.future_dates[0] }}</span>
              </div>
            </div>
          </section>

          <!-- 加载中 -->
          <section class="section-block" v-if="loadingResult">
            <div class="loading-area">
              <div class="loading-spinner"></div>
              <span class="loading-text">加载预测结果...</span>
            </div>
          </section>
        </template>

        <!-- ==================== -->
        <!-- 通用智能体布局 -->
        <!-- ==================== -->
        <template v-else>
          <!-- 1. 数据选择 -->
          <section class="section-block">
            <div class="section-head">
              <span class="section-badge">01</span>
              <div>
                <h2 class="section-title">
                  <el-icon><DataAnalysis /></el-icon>数据选择
                </h2>
                <p class="section-hint">配置数据来源与筛选条件</p>
              </div>
            </div>
            <div class="form-grid">
              <div class="form-field">
                <label>数据来源</label>
                <el-select v-model="config.dataSource" placeholder="请选择" class="field-input">
                  <el-option label="历史用气数据" value="history" />
                  <el-option label="市场交易数据" value="market" />
                  <el-option label="客户行为数据" value="customer" />
                </el-select>
              </div>
              <div class="form-field">
                <label>时间范围</label>
                <el-date-picker
                  v-model="config.dateRange"
                  type="daterange"
                  range-separator="-"
                  start-placeholder="开始"
                  end-placeholder="结束"
                  class="field-input"
                />
              </div>
              <div class="form-field full">
                <label>区域选择</label>
                <el-select v-model="config.region" multiple placeholder="选择区域" class="field-input">
                  <el-option label="华东" value="east" />
                  <el-option label="华北" value="north" />
                  <el-option label="华南" value="south" />
                  <el-option label="西部" value="west" />
                </el-select>
              </div>
            </div>
          </section>

          <!-- 2. 模型选择 -->
          <section class="section-block">
            <div class="section-head">
              <span class="section-badge">02</span>
              <div>
                <h2 class="section-title">
                  <el-icon><Cpu /></el-icon>模型选择
                </h2>
                <p class="section-hint">选择算法并调整参数</p>
              </div>
            </div>
            <div class="model-grid">
              <div
                v-for="m in models"
                :key="m.id"
                class="model-item"
                :class="{ selected: config.model === m.id, disabled: !m.available }"
                @click="m.available && (config.model = m.id)"
              >
                <div class="model-check" v-if="config.model === m.id">
                  <svg viewBox="0 0 16 16" fill="none">
                    <path
                      d="M3 8L7 12L13 4"
                      stroke="white"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </div>
                <div class="model-name">{{ m.name }}</div>
                <div class="model-desc">{{ m.description }}</div>
              </div>
            </div>
            <div class="param-section">
              <div class="param-row">
                <span class="param-label">{{ forecastLabel }}</span>
                <div class="param-control">
                  <el-slider
                    v-model="config.forecastDays"
                    :min="forecastMin"
                    :max="forecastMax"
                    :step="forecastStep"
                    class="slider"
                  />
                  <span class="param-val">{{ config.forecastDays }} {{ forecastUnit }}</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 3. 运行模型 -->
          <section class="section-block">
            <div class="section-head">
              <span class="section-badge">03</span>
              <div>
                <h2 class="section-title">
                  <el-icon><VideoPlay /></el-icon>运行模型
                </h2>
                <p class="section-hint">启动预测流程</p>
              </div>
            </div>
            <div class="run-area">
              <button class="run-btn" :disabled="isRunning" @click="runModel">
                <span v-if="!isRunning" class="run-btn-text">
                  <svg viewBox="0 0 20 20" fill="currentColor" class="play-icon"><path d="M6 4L16 10L6 16V4Z" /></svg>
                  开始运行
                </span>
                <span v-else class="run-btn-text">
                  <span class="spinner"></span>
                  运行中...
                </span>
              </button>
              <div v-if="progressSteps.length > 0" class="progress-tracker">
                <div v-for="(step, idx) in progressSteps" :key="idx" class="tracker-item" :class="step.status">
                  <div class="tracker-dot">
                    <svg v-if="step.status === 'done'" viewBox="0 0 16 16" fill="none">
                      <path
                        d="M3 8L7 12L13 4"
                        stroke="currentColor"
                        stroke-width="2.5"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                      />
                    </svg>
                    <span v-else-if="step.status === 'active'" class="dot-pulse"></span>
                  </div>
                  <span class="tracker-text">{{ step.text }}</span>
                </div>
              </div>
              <div v-if="modelResult" class="result-cards">
                <div class="result-card">
                  <el-icon class="result-icon"><TrendCharts /></el-icon>
                  <span class="result-label">{{ totalLabel }}</span>
                  <span class="result-value">{{ modelResult.total_volume }}</span>
                </div>
                <div class="result-card">
                  <el-icon class="result-icon"><DataLine /></el-icon>
                  <span class="result-label">MAE</span>
                  <span class="result-value">{{ modelResult.model_metrics?.mae || '-' }}</span>
                </div>
                <div class="result-card">
                  <el-icon class="result-icon"><Finished /></el-icon>
                  <span class="result-label">R²</span>
                  <span class="result-value">{{ modelResult.model_metrics?.r2 || '-' }}</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 4. 可视化 -->
          <section class="section-block" v-if="modelResult">
            <div class="section-head">
              <span class="section-badge">04</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title">
                    <el-icon><PieChart /></el-icon>可视化输出
                  </h2>
                  <p class="section-hint">预测结果趋势图</p>
                </div>
                <div class="chart-toggle">
                  <button
                    v-for="t in chartTypes"
                    :key="t.key"
                    class="toggle-btn"
                    :class="{ active: chartType === t.key }"
                    @click="chartType = t.key"
                  >
                    {{ t.label }}
                  </button>
                </div>
              </div>
            </div>
            <div class="chart-box">
              <div ref="chartRef" class="chart"></div>
            </div>
          </section>
        </template>
      </div>
    </div>

    <!-- 拖动分隔条 -->
    <div class="splitter" @mousedown="startDrag">
      <div class="splitter-line"></div>
    </div>

    <!-- 右侧 AI 对话 -->
    <aside class="ai-panel" :style="{ flexBasis: (1 - splitRatio) * 100 + '%' }">
      <div class="ai-header">
        <div class="ai-title-wrap">
          <div class="ai-avatar">
            <svg viewBox="0 0 24 24" fill="none">
              <path
                d="M12 2L2 7V17L12 22L22 17V7L12 2Z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linejoin="round"
              />
              <path d="M12 2V22M2 7L22 17M22 7L2 17" stroke="currentColor" stroke-width="1" opacity="0.3" />
            </svg>
          </div>
          <div>
            <div class="ai-title">{{ currentAgent.name }}助手</div>
            <div v-if="currentResult?.forecast_batch_no" class="ai-context-batch">
              当前预测：{{ currentResult.forecast_batch_no }}
            </div>
            <div class="ai-status"><span class="status-dot"></span> 在线</div>
          </div>
        </div>
        <div class="ai-header-actions">
          <el-dropdown trigger="click" @command="switchAgentByPath">
            <button class="agent-switch-button" type="button">
              切换智能体
              <span>⌄</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="item in agentOptions"
                  :key="item.id"
                  :command="item.path"
                  :disabled="item.id === agentId"
                >{{ item.name }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <button class="ai-clear" @click="clearChat">清空</button>
        </div>
      </div>
      <div class="ai-messages" ref="messagesRef" @scroll="loadOlderMessagesOnScroll">
        <template v-for="msg in messages" :key="msg.id">
        <div v-show="msg.role === 'user' || msg.content" class="msg" :class="[msg.role, { 'task-message': Boolean(msg.taskNo) }]">
          <div class="msg-avatar" :class="msg.role">
            <span v-if="msg.role === 'user'">我</span>
            <svg v-else viewBox="0 0 24 24" fill="none">
              <path
                d="M12 2L2 7V17L12 22L22 17V7L12 2Z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linejoin="round"
              />
            </svg>
          </div>
          <div class="msg-body" :class="{ 'has-task-card': Boolean(msg.taskNo) }">
            <div v-if="msg.role === 'user'" class="msg-bubble msg-bubble-plain">{{ msg.content }}</div>
            <div v-else class="msg-bubble" v-html="sanitizeHtml(msg.content)"></div>
            <section v-if="msg.role === 'assistant' && chatListRows(msg).length" class="chat-result-list">
              <div class="chat-result-list-head">查询结果 <span>共 {{ chatListRows(msg).length }} 条</span></div>
              <el-table :data="chatListRows(msg)" height="360" stripe size="small">
                <el-table-column v-for="column in chatListColumns(msg)" :key="column.key" :prop="column.key" :label="column.label" min-width="130" show-overflow-tooltip />
              </el-table>
            </section>
          </div>
        </div>
        <!-- 执行任务不是普通消息气泡的一部分：独立占满聊天栏，避免图表和长文本被气泡宽度裁切。 -->
        <AiExecutionTask v-if="msg.role === 'assistant' && msg.taskNo" class="task-execution-block" :task-no="msg.taskNo" @forecast-completed="(result) => showCompletedTaskForecast(msg, result)" @content-rendered="scrollToBottom" />
        </template>
        <div v-if="isStreaming && !messages[messages.length - 1]?.content" class="msg assistant">
          <div class="msg-avatar assistant">
            <svg viewBox="0 0 24 24" fill="none">
              <path
                d="M12 2L2 7V17L12 22L22 17V7L12 2Z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linejoin="round"
              />
            </svg>
          </div>
          <div class="msg-body">
            <div class="msg-bubble typing"><span></span><span></span><span></span></div>
          </div>
        </div>
      </div>
      <div class="ai-input">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="2"
          :placeholder="chatInputPlaceholder"
          @keydown.enter.exact.prevent="sendMessage()"
          resize="none"
        />
        <button v-if="isStreaming" class="send-btn stop-btn" @click="stopStreaming">
          <svg viewBox="0 0 20 20" fill="currentColor"><rect x="5" y="5" width="10" height="10" rx="2" /></svg>
        </button>
        <button v-else class="send-btn" :disabled="!inputMessage.trim()" @click="sendMessage()">
          <svg viewBox="0 0 20 20" fill="currentColor"><path d="M2 10L18 2L10 18L8 11L2 10Z" /></svg>
        </button>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, watch, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import echarts from '@/utils/echarts'
import { getJson, listPage, postJson, postSse } from '@/api/management'
import { renderMarkdown, sanitizeHtml } from '@/utils/markdown'
import { ElMessage } from 'element-plus'
import {
  Cpu,
  DataAnalysis,
  DataLine,
  Finished,
  Grid,
  Histogram,
  Location,
  MagicStick,
  PieChart,
  TrendCharts,
  VideoPlay
} from '@element-plus/icons-vue'
import type { ChatMessage } from '@/types'
import AiExecutionTask from '@/views/ai/AiExecutionTask.vue'
import ForecastComparisonChart from '@/components/ForecastComparisonChart.vue'

const route = useRoute()
const router = useRouter()
const chartRef = ref<HTMLElement>()
const chartRefTop = ref<HTMLElement>()
const chartRefBottom = ref<HTMLElement>()
const messagesRef = ref<HTMLElement>()
const agentId = computed(() => String(route.meta.agentId || 'winter-supply'))
const activeChatId = ref(agentId.value)
const isMonthlySales = computed(() => agentId.value === 'monthly-sales')
const isShortTerm = computed(() => agentId.value === 'short-term')
const isWinterSupply = computed(() => agentId.value === 'winter-supply')
const agentOptions = [
  { id: 'winter-supply', name: '冬季保供预测', path: '/agent/winter-supply' },
  { id: 'monthly-sales', name: '月度销量预测', path: '/agent/monthly-sales' },
  { id: 'short-term', name: '短期客户预测', path: '/agent/short-term' }
]
const agentTabMeta: Record<string, { description: string; icon: string }> = {
  'winter-supply': { description: '供需平衡 · 缺口预警', icon: 'M12 2 2 22h20L12 2Zm0 6v6m0 3v1' },
  'monthly-sales': { description: '月度趋势 · 同比分析', icon: 'M3 3v18h18M7 14l4-4 4 4 6-6' },
  'short-term': { description: '客户用气 · 短期预测', icon: 'M17 21v-2c0-3-2-5-5-5s-5 2-5 5v2m5-10a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z' }
}
const agentTabIcon = (id: string) => agentTabMeta[id]?.icon || agentTabMeta['winter-supply'].icon
const agentTabDescription = (id: string) => agentTabMeta[id]?.description || '智能预测分析'
const currentAgent = computed(() => agentOptions.find((item) => item.id === agentId.value) || agentOptions[0])
const switchAgentByPath = (path: string) => {
  if (path && path !== route.path) void router.push(path)
}

const chartTypes = [
  { key: 'line', label: '折线' },
  { key: 'bar', label: '柱状' },
  { key: 'area', label: '面积' }
]
const futureChartTypes = [
  { key: 'bar', label: '柱状' },
  { key: 'line', label: '折线' },
  { key: 'table', label: '数据' }
]

const isRunning = ref(false)
const chartType = ref('line')
const futureChartType = ref('bar')
const streamingSet = reactive(new Set<string>())
const isStreaming = computed(() => streamingSet.has(activeChatId.value))
const abortControllers = new Map<string, AbortController>()
const inputMessage = ref('')

const chatListRows = (message: ChatMessage): Record<string, any>[] => {
  const result = message.executionResult
  const records = result?.data?.records || result?.records
  return Array.isArray(records) ? records : []
}

const chatListColumns = (message: ChatMessage) => {
  const first = chatListRows(message)[0] || {}
  const labels: Record<string, string> = {
    regionName: '地区',
    industryName: '行业',
    customerName: '客户'
  }
  return Object.keys(labels)
    .filter((key) => key in first)
    .map((key) => ({ key, label: labels[key] }))
}

// === 拖动分隔条 ===
const splitRatio = ref(0.7)
const isDragging = ref(false)

const startDrag = (e: MouseEvent) => {
  e.preventDefault()
  isDragging.value = true
  const container = (e.currentTarget as HTMLElement).parentElement
  if (!container) return
  const containerWidth = container.offsetWidth

  const onMove = (ev: MouseEvent) => {
    const rect = container.getBoundingClientRect()
    const ratio = (ev.clientX - rect.left) / containerWidth
    splitRatio.value = Math.min(0.85, Math.max(0.3, ratio))
  }
  const onUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    // 拖动结束后重绘图表
    nextTick(() => {
      ;[chartRef, chartRefTop, chartRefBottom].forEach((r) => {
        if (r.value && (r.value as any)._echarts) (r.value as any)._echarts.resize()
      })
    })
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}
const modelResult = ref<any>(null)
const agentInfo = ref<any>(null)
const progressSteps = ref<{ text: string; status: 'pending' | 'active' | 'done' }[]>([])

const config = reactive({
  dataSource: 'history',
  dateRange: [] as any[],
  region: ['east', 'north'],
  model: 'default',
  forecastDays: 30
})

// 月度预测专用状态
const monthlyRegions = [
  { label: '江苏', value: '江苏' },
  { label: '河北', value: '河北' }
]
const industries = ['城市燃气', '工业燃料', '天然气化工', '天然气发电', 'CNG', 'LNG']
const industryNameMap: Record<string, string> = {
  城市燃气: '城市燃气',
  工业燃料: '工业燃料',
  天然气化工: '化工',
  天然气发电: '发电',
  CNG: 'CNG',
  LNG: 'LNG'
}
const industryColors: Record<string, string> = {
  城市燃气: 'linear-gradient(135deg, #0EA5E9, #0284C7)',
  工业燃料: 'linear-gradient(135deg, #10B981, #059669)',
  天然气化工: 'linear-gradient(135deg, #F59E0B, #D97706)',
  天然气发电: 'linear-gradient(135deg, #8B5CF6, #6D28D9)',
  CNG: 'linear-gradient(135deg, #EC4899, #DB2777)',
  LNG: 'linear-gradient(135deg, #06B6D4, #0891B2)'
}
const selectedProvince = ref('江苏')
const selectedIndustry = ref('城市燃气')
const currentResult = ref<any>(null)
const loadingResult = ref(false)
const provinceMape = ref<Record<string, number>>({})

const shortTermFutureValues = computed<number[]>(() => currentResult.value?.future_predicted || [])
const shortTermForecastTotal = computed(() => {
  const total = shortTermFutureValues.value.reduce((sum, value) => sum + Number(value || 0), 0)
  return total ? total.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) : '-'
})
const shortTermComparablePairs = computed(() => {
  const references = currentResult.value?.last_year_values || []
  return shortTermFutureValues.value.flatMap((value, index) => {
    const reference = references[index]
    return reference == null || !Number.isFinite(Number(reference)) ? [] : [{ forecast: Number(value), reference: Number(reference) }]
  })
})
const shortTermLastYearTotal = computed(() => {
  const total = shortTermComparablePairs.value.reduce((sum, point) => sum + point.reference, 0)
  return shortTermComparablePairs.value.length
    ? total.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
    : '-'
})
const shortTermTrendRate = computed(() => {
  if (shortTermComparablePairs.value.length) {
    const forecastTotal = shortTermComparablePairs.value.reduce((sum, point) => sum + point.forecast, 0)
    const referenceTotal = shortTermComparablePairs.value.reduce((sum, point) => sum + point.reference, 0)
    return referenceTotal === 0 ? 0 : ((forecastTotal - referenceTotal) / Math.abs(referenceTotal)) * 100
  }
  const values = shortTermFutureValues.value
  if (values.length < 2 || !Number(values[0])) return 0
  return ((Number(values[values.length - 1]) - Number(values[0])) / Math.abs(Number(values[0]))) * 100
})
const shortTermTrendText = computed(() => {
  const rate = shortTermTrendRate.value
  return `${rate > 0 ? '+' : ''}${rate.toFixed(2)}%`
})
const shortTermTrendDescription = computed(() => {
  const rate = shortTermTrendRate.value
  if (Math.abs(rate) < 1) return `总体保持平稳，略有${rate >= 0 ? '上升' : '下降'}`
  return `较去年同期${rate > 0 ? '有所上升' : '有所下降'}`
})
const shortTermTrendTone = computed(() => {
  const rate = shortTermTrendRate.value
  if (Math.abs(rate) < 1) return 'flat'
  return rate > 0 ? 'up' : 'down'
})
const formatShortTermStats = (values: number[]) => {
  if (!values.length) return '-'
  const total = values.reduce((sum, value) => sum + value, 0)
  const format = (value: number) => value.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
  return `日均 ${format(total / values.length)} ｜ 峰值 ${format(Math.max(...values))} ｜ 谷值 ${format(Math.min(...values))}`
}
const shortTermLastYearStats = computed(() => formatShortTermStats(shortTermComparablePairs.value.map((point) => point.reference)))
const shortTermForecastStats = computed(() => formatShortTermStats(shortTermFutureValues.value.map((value) => Number(value))))
const formatShortTermMetric = (value: unknown, suffix = '') => {
  if (value === null || value === undefined || value === '' || !Number.isFinite(Number(value))) return '-'
  return `${Number(value).toFixed(2)}${suffix}`
}
const shortTermFutureRows = computed(() => {
  const dates = currentResult.value?.future_dates || []
  const samePeriodValues = currentResult.value?.last_year_values || []
  return dates.map((date: string, index: number) => {
    const value = Number(shortTermFutureValues.value[index])
    const samePeriodValue = samePeriodValues[index]
    const reference = samePeriodValue == null || !Number.isFinite(Number(samePeriodValue)) ? null : Number(samePeriodValue)
    const yearOnYearIncrease = reference === null ? null : value - reference
    const yearOnYearGrowthRate = yearOnYearIncrease === null || reference === 0 ? null : (yearOnYearIncrease / Math.abs(reference)) * 100
    const signed = (number: number, suffix = '') => `${number > 0 ? '+' : ''}${number.toFixed(2)}${suffix}`
    return {
      date,
      samePeriodValue: samePeriodValue ?? '-',
      value: shortTermFutureValues.value[index] ?? '-',
      yearOnYearIncreaseText: yearOnYearIncrease === null ? '-' : signed(yearOnYearIncrease),
      yearOnYearGrowthRateText: yearOnYearGrowthRate === null ? '-' : signed(yearOnYearGrowthRate, '%'),
      yearOnYearTone: yearOnYearIncrease === null || yearOnYearIncrease === 0 ? '' : yearOnYearIncrease > 0 ? 'daily-up' : 'daily-down'
    }
  })
})
const shortTermForecastStart = computed(() => currentResult.value?.future_dates?.[0] || '-')

const mapeClass = (mape: number) => {
  if (mape <= 10) return 'metric-good'
  if (mape <= 30) return 'metric-ok'
  return 'metric-bad'
}

// 通用智能体的计算属性
const forecastLabel = computed(() => (agentId.value === 'monthly-sales' ? '预测月数' : '预测天数'))
const forecastUnit = computed(() => (agentId.value === 'monthly-sales' ? '月' : '天'))
const forecastMin = computed(() => (agentId.value === 'monthly-sales' ? 3 : 7))
const forecastMax = computed(() => (agentId.value === 'monthly-sales' ? 12 : 90))
const forecastStep = computed(() => (agentId.value === 'monthly-sales' ? 1 : 7))
const totalLabel = computed(() =>
  agentId.value === 'monthly-sales' ? '预测总销量' : agentId.value === 'short-term' ? '预测总用气量' : '预测总需求量'
)

const models = computed(() => {
  const base = [
    { id: 'default', name: '默认模型', description: '', available: true },
    { id: 'prophet', name: 'Prophet', description: 'Facebook时间序列', available: false },
    { id: 'lstm', name: 'LSTM', description: '神经网络', available: false },
    { id: 'hybrid', name: '混合模型', description: '多模型融合', available: false }
  ]
  if (agentId.value === 'winter-supply') base[0].description = 'GradientBoosting'
  else if (agentId.value === 'monthly-sales') base[0].description = 'Ridge + 季节特征'
  else if (agentId.value === 'short-term') base[0].description = 'RandomForest + KMeans'
  return base
})

/** 统一预测记录是三个智能体结果页唯一的数据来源，不再请求已删除的旧结果接口。 */
const jsonValue = (value: unknown) => {
  if (typeof value !== 'string') return value && typeof value === 'object' ? value : {}
  try { return JSON.parse(value) } catch { return {} }
}
const latestExecuteRecord = async (agentCode: string, predicate: (row: any) => boolean = () => true) => {
  const page = await listPage('/model-execute-record', { page: 1, size: 500, agentCode, status: 'SUCCESS' })
  return page.records.find(predicate) || null
}
/** 当前会话优先：从最后一个预测任务反查它实际产出的预测记录。没有预测任务时才回退到任意历史成功记录。 */
const latestConversationExecuteRecord = async () => {
  const taskMessages = [...messages.value].reverse().filter((message) => Boolean(message.taskNo))
  for (const message of taskMessages) {
    try {
      const detailsResponse = await getJson<any>(`/execution-tasks/${encodeURIComponent(message.taskNo!)}/details?pageNo=1&pageSize=100&status=SUCCESS`)
      const details = detailsResponse.data?.records || []
      const detail = [...details].reverse().find((item: any) => item.forecastNo)
      if (!detail?.forecastNo) continue
      const recordResponse = await getJson<any>(`/model-execute-record/${encodeURIComponent(detail.forecastNo)}`)
      const record = recordResponse.data
      if (record?.status === 'SUCCESS' && record.agentCode === agentId.value) return record
    } catch (error) {
      console.warn('读取当前会话预测结果失败，将尝试下一条任务：', error)
    }
  }
  return null
}
const latestConversationTaskRecord = async () => {
  const taskMessages = [...messages.value].reverse().filter((message) => Boolean(message.taskNo))
  for (const message of taskMessages) {
    try {
      const details = (await getJson<any>(`/execution-tasks/${encodeURIComponent(message.taskNo!)}/details?pageNo=1&pageSize=100&status=SUCCESS`)).data?.records || []
      const detail = [...details].reverse().find((item: any) => item.detailNo && item.forecastNo)
      if (!detail) continue
      const result = await getJson<any>(`/execution-tasks/details/${encodeURIComponent(detail.detailNo)}/result`)
      const payload = result.data || {}
      const record = payload.record
      // 任务结果接口把三段模型快照拆在 data 顶层；合并后交给统一转换器，
      // 否则只有摘要指标，没有可绘制的回测及未来点位。
      if (record?.status === 'SUCCESS' && record.agentCode === agentId.value) {
        return {
          ...record,
          trainingResultJson: payload.training ?? record.trainingResultJson,
          backtestResultJson: payload.backtest ?? record.backtestResultJson,
          forecastResultJson: payload.forecast ?? record.forecastResultJson,
          lastYearPoints: payload.lastYearPoints ?? record.lastYearPoints
        }
      }
    } catch { /* 继续回退到按客户编码查询 */ }
  }
  return null
}
const resultFromExecuteRecord = (record: any) => {
  const backtest = jsonValue(record?.backtestResultJson) as any
  const forecast = jsonValue(record?.forecastResultJson) as any
  const backtestPoints = Array.isArray(backtest?.points) ? backtest.points
    : Array.isArray(backtest?.rolling_backtest_results) ? backtest.rolling_backtest_results : []
  const forecastPoints = Array.isArray(forecast?.points) ? forecast.points : []
  const dateOf = (point: any) => point?.date || point?.stat_date || point?.statDate || point?.forecast_date || point?.forecastDate
  const actualOf = (point: any) => point?.actual_value ?? point?.actualValue ?? point?.actual
  const predictedOf = (point: any) => point?.predicted_value ?? point?.predictedValue ?? point?.predicted
  const valueOf = (point: any) => point?.forecast_value ?? point?.forecastValue ?? point?.prediction ?? point?.value
  const lastYearValueByDate = new Map((Array.isArray(record?.lastYearPoints) ? record.lastYearPoints : [])
    .map((point: any) => [dateOf(point), valueOf(point)]))
  return {
    dates: backtestPoints.map(dateOf).filter(Boolean), actual: backtestPoints.map(actualOf), predicted: backtestPoints.map(predictedOf),
    history_dates: backtestPoints.map(dateOf).filter(Boolean), history_values: backtestPoints.map(actualOf),
    future_dates: forecastPoints.map(dateOf).filter(Boolean), future_predicted: forecastPoints.map(valueOf),
    last_year_values: forecastPoints.map((point: any) => lastYearValueByDate.get(dateOf(point)) ?? null),
    metrics: { mape: record?.mape, wmape: record?.wmape, smape: record?.smape, rmse: record?.rmse, mae: record?.mae, r2: record?.r2 },
    bestModel: record?.bestModel, forecastNo: record?.forecastNo, status: record?.status,
    analysisContent: record?.analysisContent,
    regionName: record?.regionName, industryName: record?.industryName, customerName: record?.customerName
  }
}

// === 月度预测：加载结果 ===
const loadProvinceMape = async () => {
  try {
    const records = (await listPage('/model-execute-record', { page: 1, size: 500, agentCode: 'monthly-sales', status: 'SUCCESS' })).records
    const mapeMap: Record<string, number> = {}
    for (const r of records) {
      if (r.regionName === selectedProvince.value && r.industryName) {
        mapeMap[r.industryName] = r.mape
      }
    }
    provinceMape.value = mapeMap
  } catch (e) {
    console.error('加载省份MAPE失败:', e)
  }
}

const monthKey = (date: string) => date.slice(0, 7)

const aggregateMonthlySeries = (dates: string[] = [], values: number[] = []) => {
  const grouped = new Map<string, number>()
  dates.forEach((date, index) => {
    const value = Number(values[index])
    if (!Number.isFinite(value)) return
    const key = monthKey(date)
    grouped.set(key, (grouped.get(key) || 0) + value)
  })
  return {
    dates: Array.from(grouped.keys()),
    values: Array.from(grouped.values()).map((value) => Number(value.toFixed(2)))
  }
}

const normalizeMonthlyResult = (data: any) => {
  const actual = aggregateMonthlySeries(data.dates, data.actual)
  const predicted = aggregateMonthlySeries(data.dates, data.predicted)
  const history = aggregateMonthlySeries(data.history_dates || data.dates, data.history_values || data.actual)
  const hasFuture = Array.isArray(data.future_dates)
  const future = hasFuture ? aggregateMonthlySeries(data.future_dates, data.future_predicted) : undefined

  return {
    ...data,
    dates: actual.dates,
    actual: actual.values,
    predicted: predicted.values,
    history_dates: history.dates,
    history_values: history.values,
    future_dates: future?.dates,
    future_predicted: future?.values
  }
}

const loadMonthlyResult = async () => {
  loadingResult.value = true
  currentResult.value = null
  try {
    const industryKey = industryNameMap[selectedIndustry.value] || selectedIndustry.value
    const conversationRecord = await latestConversationExecuteRecord()
    const record = conversationRecord || await latestExecuteRecord('monthly-sales', row => row.regionName === selectedProvince.value && row.industryName === industryKey)
    if (record) {
      if (conversationRecord) {
        selectedProvince.value = record.regionName || selectedProvince.value
        const matchedIndustry = Object.entries(industryNameMap).find(([, value]) => value === record.industryName)?.[0]
        selectedIndustry.value = matchedIndustry || record.industryName || selectedIndustry.value
      }
      const monthlyData = normalizeMonthlyResult(resultFromExecuteRecord(record))
      currentResult.value = monthlyData
      nextTick(() => initMonthlyChart(monthlyData))
    }
  } catch (e) {
    console.error('加载月度预测结果失败:', e)
  } finally {
    loadingResult.value = false
  }
}

const switchProvince = (province: string) => {
  selectedProvince.value = province
  loadProvinceMape()
  loadMonthlyResult()
}

const switchIndustry = (industry: string) => {
  selectedIndustry.value = industry
  loadMonthlyResult()
}

// === 短期客户预测专用状态 ===
const stProvinces = ref<string[]>([])
const selectedStProvince = ref('')
const stIndustries = ref<string[]>([])
const selectedStIndustry = ref('')
const selectedStCustomer = ref('')
const stCustomers = ref<string[]>([])
const stCustomerRecords = ref<Record<string, any>[]>([])
const stProvinceMape = ref<Record<string, number>>({})
const stAllResults = ref<any[]>([])
const stFilterDirty = ref(false)
const runningForecast = ref(false)
/** 每个智能体右侧会话面板对应的服务端会话编号。 */
const conversationMemory = new Map<string, string>()
let stResultRequestSeq = 0

const isForecastAnalysisPage = computed(() => isMonthlySales.value || isShortTerm.value || isWinterSupply.value)
const chatInputPlaceholder = computed(() =>
  isForecastAnalysisPage.value
    ? '输入问题；业务对象不明确时，助手会向您确认...'
    : '请输入问题...'
)

/** 当前短期预测选择对应的唯一客户记录。 */
const selectedShortTermCustomerRecord = () =>
  stCustomerRecords.value.find(
    (item) => item.customerName === selectedStCustomer.value
      && item.regionName === selectedStProvince.value
      && item.industryName === selectedStIndustry.value
  )

const shortTermEmptyDescription = computed(() =>
  stFilterDirty.value
    ? '正在获取所选范围的预测结果'
    : `${selectedStProvince.value || '当前地区'} / ${selectedStIndustry.value || '当前行业'} / ${selectedStCustomer.value || '当前客户'} 暂无预测结果`
)

const queryString = (params: Record<string, string>) => new URLSearchParams(params).toString()

const syncShortTermCustomers = () => {
  const customerSet = new Set<string>(
    stCustomerRecords.value
      .filter((item) => item.regionName === selectedStProvince.value && item.industryName === selectedStIndustry.value)
      .map((item) => item.customerName)
      .filter(Boolean)
  )
  stCustomers.value = Array.from(customerSet)
  if (selectedStCustomer.value && !stCustomers.value.includes(selectedStCustomer.value)) {
    selectedStCustomer.value = ''
  }
}

const selectDefaultShortTermCustomer = () => {
  if (!selectedStCustomer.value && stCustomers.value.length) {
    selectedStCustomer.value = stCustomers.value[0]
  }
}

const syncShortTermMape = () => {
  const scopedResults = stAllResults.value.filter((r) => r.province === selectedStProvince.value)
  const mapeMap: Record<string, number> = {}
  for (const result of scopedResults) {
    if (!result.customer && result.metrics?.mape !== undefined) {
      mapeMap[result.industry] = result.metrics.mape
    }
  }
  stProvinceMape.value = mapeMap
}

const loadStDimensions = async () => {
  try {
    const [regions, industries, customers] = await Promise.all([
      listPage('/base-region', { page: 1, size: 1000 }),
      listPage('/base-industry', { page: 1, size: 1000 }),
      listPage('/base-customer', { page: 1, size: 1000 })
    ])
    stProvinces.value = Array.from(
      new Set(regions.records.map((item: Record<string, any>) => item.regionName).filter(Boolean))
    )
    stIndustries.value = Array.from(
      new Set(industries.records.map((item: Record<string, any>) => item.industryName).filter(Boolean))
    )
    stCustomerRecords.value = customers.records
  } catch (e) {
    console.error('加载短期预测基础维度失败:', e)
  }
}

const loadStMape = async () => {
  try {
    const records = (await listPage('/model-execute-record', { page: 1, size: 500, agentCode: 'short-term', status: 'SUCCESS' })).records
    stAllResults.value = records.map(row => ({ province: row.regionName, industry: row.industryName, customer: row.customerName, metrics: { mape: row.mape } }))
  } catch (e) {
    console.error('加载短期MAPE失败:', e)
    stAllResults.value = []
  }
}

/**
 * 加载左侧当前筛选范围的结果。
 * 仅首次进入页面时允许按当前会话最新任务自动聚焦；用户手动切换筛选后，
 * 绝不能再用右侧任务范围覆盖用户刚刚选择的地区、行业或客户。
 */
const loadStResult = async (preferConversation = false) => {
  if (!selectedStProvince.value || !selectedStIndustry.value || !selectedStCustomer.value) {
    currentResult.value = null
    return
  }
  const requestSeq = ++stResultRequestSeq
  stFilterDirty.value = false
  loadingResult.value = true
  currentResult.value = null
  try {
    // 会话恢复只用于自动聚焦，不得阻塞当前已选客户的结果展示。历史会话中的
    // 某条请求若因网络切换、旧代理或服务重启迟迟未结束，3 秒后直接按客户编码查询。
    const conversationRecord = preferConversation
      ? await Promise.race<any>([
          latestConversationExecuteRecord(),
          wait(3_000).then(() => null)
        ])
      : null
    if (conversationRecord) {
      await focusShortTermForecastScope({
        regionName: conversationRecord.regionName,
        industryName: conversationRecord.industryName,
        customerName: conversationRecord.customerName
      })
      if (requestSeq !== stResultRequestSeq) return
      const data = resultFromExecuteRecord(conversationRecord)
      currentResult.value = data
      nextTick(() => initMonthlyChart(data))
      return
    }
    const customer = selectedShortTermCustomerRecord()
    // 已确定客户时由后端按稳定编码过滤，避免整页记录受名称快照或分页影响。
    const directPage = customer?.customerCode
      ? await listPage('/model-execute-record', {
          page: 1, size: 1, agentCode: 'short-term', status: 'SUCCESS', customerCode: customer.customerCode
        })
      : { records: [] as any[] }
    const record = directPage.records[0] || await latestExecuteRecord('short-term', row =>
      row.regionName === selectedStProvince.value
      && row.industryName === selectedStIndustry.value && row.customerName === selectedStCustomer.value)
    // 页面恢复会同步多个下拉框并产生新的请求序号；不能因此丢掉已经返回的
    // 同一客户结果。只要记录仍属于当前已选客户，即可安全回填。
    if (record && (!selectedShortTermCustomerRecord()?.customerCode || record.customerCode === selectedShortTermCustomerRecord()?.customerCode)) {
      const data = resultFromExecuteRecord(record)
      currentResult.value = data
      stFilterDirty.value = false
      nextTick(() => initMonthlyChart(data))
    }
  } catch (e) {
    console.error('加载短期预测结果失败:', e)
  } finally {
    if (requestSeq === stResultRequestSeq) {
      loadingResult.value = false
    }
  }
}

const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))

const shortTermForecastContext = (forecastHorizon?: number) => {
  const customer = selectedShortTermCustomerRecord()
  return {
    regionCode: customer?.regionCode,
    regionName: selectedStProvince.value,
    industryCode: customer?.industryCode,
    industryName: selectedStIndustry.value,
    customerCode: customer?.customerCode,
    customerName: selectedStCustomer.value,
    ...(forecastHorizon ? { forecastHorizon } : {})
  }
}

const setShortTermForecastMessage = (
  messageId: string,
  text: string,
  chatId = activeChatId.value,
  executionResult?: Record<string, any>
) => {
  const targetMessages = chatId === activeChatId.value ? messages.value : chatMemory.get(chatId)
  const message = targetMessages?.find((item) => item.id === messageId)
  if (message) {
    message.content = renderMarkdown(text)
    if (executionResult) message.executionResult = executionResult
    if (chatId === activeChatId.value) {
      saveChatToMemory(chatId)
      scrollToBottom()
    }
  }
}

type ShortTermForecastScope = {
  regionName?: string
  industryName?: string
  customerName?: string
}

const forecastScopeFromResponse = (data: any): ShortTermForecastScope => {
  const execution = data?.executionResult || data?.execution_result || data?.resultData?.executionResult || {}
  const payload = execution?.data || execution
  const task = Array.isArray(payload?.tasks) ? payload.tasks[0] || {} : payload
  const result = data?.forecastResult || payload?.forecastResult || {}
  return {
    regionName: task?.regionName || task?.region_name || result?.regionName || result?.region_name,
    industryName: task?.industryName || task?.industry_name || result?.industryName || result?.industry_name,
    customerName: task?.customerName || task?.customer_name || result?.customerName || result?.customer_name
  }
}

const forecastResultMessage = (data: any, scope: ShortTermForecastScope) => {
  const result = data?.forecastResult || {}
  const scopeText = [scope.regionName, scope.industryName, scope.customerName].filter(Boolean).join(' / ')
  const lines = [
    '预测已完成',
    ...(scopeText ? [`- 预测范围：${scopeText}`] : []),
    ...(data?.forecastNo ? [`- 预测编号：${data.forecastNo}`] : []),
    ...(result.average !== undefined ? [`- 平均预测值：${result.average}`] : []),
    ...(result.minimum !== undefined ? [`- 最小预测值：${result.minimum}`] : []),
    ...(result.maximum !== undefined ? [`- 最大预测值：${result.maximum}`] : [])
  ]
  return lines.join('\n')
}

/** 仅预测任务可改变左侧范围；训练、查询、分析结果都不调用此方法。 */
const focusShortTermForecastScope = async (scope: ShortTermForecastScope) => {
  await loadStDimensions()
  // 兼容尚未重启 Java 服务的旧任务返回：只能在客户名称在本地基础数据中
  // 唯一命中时补足范围；同名客户绝不猜测，等待服务端返回完整任务范围。
  const matched = !scope.regionName || !scope.industryName
    ? stCustomerRecords.value.filter((item) => item.customerName === scope.customerName)
    : []
  const resolved = matched.length === 1
    ? { ...scope, regionName: matched[0].regionName, industryName: matched[0].industryName }
    : scope
  if (!resolved.regionName || !resolved.industryName || !resolved.customerName) return false
  if (!stProvinces.value.includes(resolved.regionName) || !stIndustries.value.includes(resolved.industryName)) return false
  selectedStProvince.value = resolved.regionName
  selectedStIndustry.value = resolved.industryName
  syncShortTermCustomers()
  if (!stCustomers.value.includes(resolved.customerName)) return false
  selectedStCustomer.value = resolved.customerName
  syncShortTermMape()
  // 任务卡恢复完成时会再次调用本方法。这里若先清空，异步回填被后续请求覆盖时
  // 左侧就会永久停在空状态；保留旧结果直到新的同范围结果已准备好。
  stFilterDirty.value = true
  return true
}

const refreshShortTermForecastResult = async () => {
  await loadStResult(false)
  // 汇总指标不应阻塞客户预测结果展示，也不能让已完成任务一直保持“预测中”。
  void loadStMape().then(syncShortTermMape)
}

/** 聊天任务卡已读取并校验过的预测结果，可直接作为左侧客户结果页的可靠回退。 */
const shortTermResultFromTaskPresentation = (result: Record<string, any>) => {
  const backtestPoints = Array.isArray(result.backtestPoints) ? result.backtestPoints : []
  const points = Array.isArray(result.points) ? result.points : []
  const lastYearPoints = Array.isArray(result.lastYearPoints) ? result.lastYearPoints : []
  const lastYearByDate = new Map(lastYearPoints.map((point: any) => [point.date, point.value]))
  const training = result.training || {}
  return {
    dates: backtestPoints.map((point: any) => point.date).filter(Boolean),
    actual: backtestPoints.map((point: any) => point.actualValue),
    predicted: backtestPoints.map((point: any) => point.predictedValue),
    future_dates: points.map((point: any) => point.date).filter(Boolean),
    future_predicted: points.map((point: any) => point.value),
    last_year_values: points.map((point: any) => lastYearByDate.get(point.date) ?? null),
    metrics: { mape: training.mape, wmape: training.wmape, smape: training.smape, rmse: training.rmse, mae: training.mae, r2: training.r2 },
    bestModel: training.bestModel || result.bestModel,
    forecastNo: result.batchNo || result.forecastNo,
    analysisContent: result.analysisContent || result.record?.analysisContent,
    regionName: result.regionName, industryName: result.industryName, customerName: result.customerName
  }
}

/** 右侧任务流程完成后，左侧自动切到同一地区、行业、客户，并加载最新预测结果。 */
const showCompletedTaskForecast = async (message: ChatMessage, result: Record<string, any>) => {
  // 结果属于创建该任务的同一轮会话消息，避免用户必须滚动到任务卡片才能获知结论。
  message.executionResult = result
  const model = result.bestModel || result.modelName
  const mape = result.mape ?? result.metrics?.mape
  const summary = `预测已完成${model ? `，最佳模型为 **${model}**` : ''}${mape !== undefined && mape !== null ? `，回测 MAPE 为 **${mape}%**` : ''}。详细回测曲线和未来预测结果已展示在本消息下方。`
  message.content = `${message.content}<br/><br/>${renderMarkdown(summary)}`
  scrollToBottom()
  window.setTimeout(scrollToBottom, 150)
  window.setTimeout(scrollToBottom, 800)
  if (!isShortTerm.value) return
  await focusShortTermForecastScope({
    regionName: result.regionName,
    industryName: result.industryName,
    customerName: result.customerName
  })
  // 任务卡的结果已明确携带 forecastNo。按编号读取完整执行记录，避免会话恢复、
  // 分页查询或任务结果摘要的字段差异导致左侧没有点位数据。
  const forecastNo = result.forecastNo || result.batchNo
  if (forecastNo) {
    try {
      const response = await getJson<any>(`/model-execute-record/${encodeURIComponent(forecastNo)}`)
      const record = response.data
      if (record?.status === 'SUCCESS') {
        const data = resultFromExecuteRecord(record)
        if (data.future_dates.length) {
          currentResult.value = data
          loadingResult.value = false
          stFilterDirty.value = false
          await nextTick()
          initMonthlyChart(data)
          return
        }
      }
    } catch (error) {
      console.warn('按预测编号读取左侧结果失败，将使用任务卡结果：', error)
    }
  }
  // 范围聚焦会清空旧画面，随后必须立即回填任务卡已经读取成功的数据。
  // 即使历史范围无法完整恢复，也不能让已完成任务的结果消失。
  const fallback = shortTermResultFromTaskPresentation(result)
  if (fallback.future_dates.length) {
    currentResult.value = fallback
    loadingResult.value = false
    stFilterDirty.value = false
    await nextTick()
    initMonthlyChart(fallback)
  }
  // 当前任务卡与左侧展示使用同一预测批次；不再立即发起会清空画面的二次查询。
  // 用户主动切换筛选条件时，仍由切换事件按客户编码查询最新记录。
}

const runShortTermForecast = async () => {
  if (runningForecast.value || loadingResult.value || isStreaming.value) return
  if (!selectedStProvince.value || !selectedStIndustry.value || !selectedStCustomer.value) {
    ElMessage.warning('请先选择客户')
    return
  }
  // 左侧按钮只负责把当前范围带入同一套对话流程；不直接创建任务，也不预设
  // 预测周期、自动训练或确认状态。Agent 会先用结构化澄清要求填写预测天数。
  const customer = shortTermForecastContext()
  inputMessage.value = `发起${customer.regionName} ${customer.industryName} ${customer.customerName}的燃气预测`
  await sendMessage()
}

const loadShortTermDashboard = async () => {
  await Promise.all([loadStDimensions(), loadStMape()])
  const firstResult = stAllResults.value[0]
  if (!selectedStProvince.value || !stProvinces.value.includes(selectedStProvince.value)) {
    selectedStProvince.value = firstResult?.province || stProvinces.value[0] || ''
  }
  if (!selectedStIndustry.value || !stIndustries.value.includes(selectedStIndustry.value)) {
    const firstMatchedResult = stAllResults.value.find((item) => item.province === selectedStProvince.value)
    selectedStIndustry.value = firstMatchedResult?.industry || firstResult?.industry || stIndustries.value[0] || ''
  }
  syncShortTermCustomers()
  selectDefaultShortTermCustomer()
  syncShortTermMape()
  const taskRecord = await latestConversationTaskRecord()
  if (taskRecord) {
    await focusShortTermForecastScope({ regionName: taskRecord.regionName, industryName: taskRecord.industryName, customerName: taskRecord.customerName })
    const data = resultFromExecuteRecord(taskRecord)
    currentResult.value = data
    loadingResult.value = false
    await nextTick()
    initMonthlyChart(data)
    return
  }
  await loadStResult(true)
}

const switchStProvince = (province: string) => {
  selectedStProvince.value = province
  selectedStCustomer.value = ''
  syncShortTermCustomers()
  selectDefaultShortTermCustomer()
  syncShortTermMape()
  currentResult.value = null
  stFilterDirty.value = true
  void loadStResult(false)
}

const switchStIndustry = (industry: string) => {
  selectedStIndustry.value = industry
  selectedStCustomer.value = ''
  syncShortTermCustomers()
  selectDefaultShortTermCustomer()
  currentResult.value = null
  stFilterDirty.value = true
  void loadStResult(false)
}

const switchStCustomer = (customer: string) => {
  selectedStCustomer.value = customer
  if (customer) {
    const customerRecord = selectedShortTermCustomerRecord()
    if (customerRecord) {
      selectedStProvince.value = customerRecord.regionName || selectedStProvince.value
      selectedStIndustry.value = customerRecord.industryName || selectedStIndustry.value
      syncShortTermCustomers()
      syncShortTermMape()
    }
  }
  currentResult.value = null
  stFilterDirty.value = true
  void loadStResult(false)
}

const resetShortTermFilters = () => {
  const firstResult = stAllResults.value[0]
  selectedStProvince.value = firstResult?.province || stProvinces.value[0] || ''
  const firstMatchedResult = stAllResults.value.find((item) => item.province === selectedStProvince.value)
  selectedStIndustry.value = firstMatchedResult?.industry || stIndustries.value[0] || ''
  selectedStCustomer.value = ''
  syncShortTermCustomers()
  selectDefaultShortTermCustomer()
  syncShortTermMape()
  loadStResult(false)
}

// === 冬供保供预测专用状态 ===
const wsProvinces = ['江苏', '河北']
const selectedWsProvince = ref('江苏')
const wsProvinceMape = ref<Record<string, number>>({})

const loadWsMape = async () => {
  try {
    const records = (await listPage('/model-execute-record', { page: 1, size: 500, agentCode: 'winter-supply', status: 'SUCCESS' })).records
    const mapeMap: Record<string, number> = {}
    for (const r of records) {
      if (r.regionName) mapeMap[r.regionName] = r.mape
    }
    wsProvinceMape.value = mapeMap
  } catch (e) {
    console.error('加载冬供MAPE失败:', e)
  }
}

const loadWsResult = async () => {
  loadingResult.value = true
  currentResult.value = null
  try {
    const conversationRecord = await latestConversationExecuteRecord()
    const record = conversationRecord || await latestExecuteRecord('winter-supply', row => row.regionName === selectedWsProvince.value)
    if (record) {
      if (conversationRecord) selectedWsProvince.value = record.regionName || selectedWsProvince.value
      const data = resultFromExecuteRecord(record)
      currentResult.value = data
      nextTick(() => initMonthlyChart(data))
    }
  } catch (e) {
    console.error('加载冬供预测结果失败:', e)
  } finally {
    loadingResult.value = false
  }
}

const switchWsProvince = (province: string) => {
  selectedWsProvince.value = province
  loadWsResult()
}

const refreshLatestResult = async () => {
  if (loadingResult.value) return
  if (isMonthlySales.value) {
    await loadProvinceMape()
    await loadMonthlyResult()
    return
  }
  if (isShortTerm.value) {
    await loadStMape()
    await loadStResult(false)
    return
  }
  if (isWinterSupply.value) {
    await loadWsMape()
    await loadWsResult()
  }
}

const initMonthlyChart = (data: any) => {
  // === 上图：滚动回测结果 ===
  if (chartRefTop.value) {
    const previous = echarts.getInstanceByDom(chartRefTop.value)
    previous?.dispose()
    const chart = echarts.init(chartRefTop.value)
    const series: any[] = [
      {
        name: '实际值',
        type: 'line',
        smooth: true,
        data: data.actual || [],
        itemStyle: { color: '#10b981' },
        lineStyle: { width: 1.5 },
        symbol: 'circle',
        symbolSize: 3
      },
      {
        name: '预测值',
        type: chartType.value === 'bar' ? 'bar' : 'line',
        smooth: true,
        data: data.predicted || [],
        itemStyle: { color: '#3b82f6' },
        lineStyle: { width: 1.5 },
        symbol: 'square',
        symbolSize: 3,
        areaStyle: chartType.value === 'area' ? { color: 'rgba(59,130,246,0.1)' } : undefined
      }
    ]
    chart.setOption({
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255,255,255,0.95)',
        borderColor: '#E2E8F0',
        textStyle: { color: '#0F172A', fontSize: 12 }
      },
      legend: { bottom: 0, textStyle: { color: '#64748B', fontSize: 11 }, data: ['实际值', '预测值'] },
      grid: { left: '2%', right: '4%', top: '3%', bottom: 50, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: data.dates || [],
        axisLine: { lineStyle: { color: '#E2E8F0' } },
        axisLabel: { color: '#94A3B8', fontSize: 10, rotate: 30 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#F1F5F9' } },
        axisLabel: { color: '#94A3B8', fontSize: 11 }
      },
      dataZoom: [
        { type: 'inside', start: 0, end: 100, zoomOnMouseWheel: true },
        {
          type: 'slider',
          start: 0,
          end: 100,
          height: 10,
          bottom: 28,
          borderColor: '#E2E8F0',
          fillerColor: 'rgba(59,130,246,0.1)',
          handleStyle: { color: '#3b82f6' }
        }
      ],
      series
    })
    ;(chartRefTop.value as any)._echarts = chart
  }

  // === 下图：去年同期与最新预测 ===
  if (chartRefBottom.value && data.future_dates) {
    const previous = echarts.getInstanceByDom(chartRefBottom.value)
    previous?.dispose()
    const chartB = echarts.init(chartRefBottom.value)
    // 与预测批次页保持同一口径：同一预测批次下的参考值与预测值。
    const futureDates = data.future_dates || []
    const futureValues = data.future_predicted || []
    const lastYearValues = data.last_year_values || futureDates.map(() => null)

    const isFutureBar = futureChartType.value === 'bar'
    const seriesB: any[] = isFutureBar
      ? [
          {
            name: '去年同期',
            type: 'bar',
            data: lastYearValues,
            barMaxWidth: 18,
            barGap: '18%',
            itemStyle: { color: '#9ca3af' }
          },
          {
            name: '最新预测',
            type: 'bar',
            data: futureValues,
            barMaxWidth: 18,
            itemStyle: { color: '#b91c1c' }
          }
        ]
      : [
          {
            name: '去年同期',
            type: 'line',
            smooth: 0.25,
            data: lastYearValues,
            itemStyle: { color: '#9ca3af' },
            lineStyle: { width: 1.5, color: '#9ca3af', type: 'solid' },
            symbol: 'none',
            connectNulls: false
          },
          {
            name: '最新预测',
            type: 'line',
            smooth: 0.25,
            data: futureValues,
            itemStyle: { color: '#b91c1c' },
            lineStyle: { width: 1.5, color: '#b91c1c' },
            symbol: 'none',
            connectNulls: false,
            areaStyle: { color: 'rgba(185,28,28,0.08)' }
          }
        ]

    chartB.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: isFutureBar ? 'shadow' : 'line' },
        backgroundColor: 'rgba(255,255,255,0.95)',
        borderColor: '#E2E8F0',
        textStyle: { color: '#0F172A', fontSize: 12 }
      },
      legend: {
        top: 0,
        textStyle: { color: '#64748B', fontSize: 11 },
        data: ['去年同期', '最新预测']
      },
      grid: { left: '2%', right: '4%', top: 40, bottom: 36, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: isFutureBar,
        data: futureDates,
        axisLine: { lineStyle: { color: '#E2E8F0' } },
        axisLabel: { color: '#94A3B8', fontSize: 10, rotate: 30 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#F1F5F9' } },
        axisLabel: { color: '#94A3B8', fontSize: 11 }
      },
      dataZoom: [
        { type: 'inside', start: 0, end: 100, zoomOnMouseWheel: true },
        {
          type: 'slider',
          start: 0,
          end: 100,
          height: 10,
          bottom: 28,
          borderColor: '#E2E8F0',
          fillerColor: 'rgba(185,28,28,0.10)',
          handleStyle: { color: '#b91c1c' }
        }
      ],
      series: seriesB
    })
    ;(chartRefBottom.value as any)._echarts = chartB
  }
}

// === 通用智能体：加载配置和运行 ===
const loadAgentConfig = async () => {
  try {
    const resp = await fetch(`/agents/config?${queryString({ agentId: agentId.value })}`)
    agentInfo.value = await resp.json()
    if (agentInfo.value?.default_forecast_days) config.forecastDays = agentInfo.value.default_forecast_days
  } catch (e) {
    console.error(e)
  }
}

const runModel = async () => {
  isRunning.value = true
  progressSteps.value = [
    { text: '加载历史数据', status: 'pending' },
    { text: '数据预处理与特征工程', status: 'pending' },
    { text: '训练预测模型', status: 'pending' },
    { text: '执行预测计算', status: 'pending' },
    { text: '生成分析报告', status: 'pending' }
  ]
  modelResult.value = null

  try {
    const response = await fetch('/predict', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        agent_id: agentId.value,
        data_source: config.dataSource,
        date_range: config.dateRange,
        region: config.region,
        model: config.model,
        forecast_days: config.forecastDays
      })
    })
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    const nodeMap: Record<string, number> = { load_data: 0, preprocess: 1, train_model: 2, predict: 3, analyze: 4 }

    while (reader) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          try {
            const event = JSON.parse(line.slice(6))
            if (event.type === 'progress') {
              const idx = nodeMap[event.node]
              if (idx !== undefined) {
                for (let i = 0; i < idx; i++) progressSteps.value[i].status = 'done'
                progressSteps.value[idx].status = 'active'
              }
            } else if (event.type === 'result') {
              progressSteps.value.forEach((s) => (s.status = 'done'))
              modelResult.value = event.data
              nextTick(() => initChart(event.data))
              sendAnalysisMessage(event.content || '')
            }
          } catch {}
        }
      }
    }
  } catch (e) {
    console.error(e)
  } finally {
    isRunning.value = false
  }
}

const initChart = (data: any) => {
  if (!chartRef.value) return
  echarts.getInstanceByDom(chartRef.value)?.dispose()
  const chart = echarts.init(chartRef.value)
  const isShort = agentId.value === 'short-term' && data.type_chart_data

  if (isShort) {
    const colors = ['#0EA5E9', '#10B981', '#F59E0B']
    const names = ['工业用户', '商业用户', '居民用户']
    const series = Object.entries(data.type_chart_data).map(([type, values]: [string, any], i) => ({
      name: type === 'industrial' ? names[0] : type === 'commercial' ? names[1] : names[2],
      type: chartType.value === 'bar' ? 'bar' : 'line',
      smooth: true,
      data: values,
      itemStyle: { color: colors[i % 3] },
      areaStyle: chartType.value === 'area' ? { opacity: 0.15 } : undefined,
      lineStyle: { width: 2.5 }
    }))
    chart.setOption({
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255,255,255,0.95)',
        borderColor: '#E2E8F0',
        textStyle: { color: '#0F172A', fontSize: 12 }
      },
      legend: { data: series.map((s) => s.name), bottom: 0, textStyle: { color: '#64748B', fontSize: 12 } },
      grid: { left: '2%', right: '4%', top: '4%', bottom: '12%', containLabel: true },
      xAxis: {
        type: 'category',
        data: data.dates,
        boundaryGap: false,
        axisLine: { lineStyle: { color: '#E2E8F0' } },
        axisLabel: { color: '#94A3B8', fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#F1F5F9' } },
        axisLabel: { color: '#94A3B8', fontSize: 11 }
      },
      series
    })
  } else {
    const series: any[] = [
      {
        name: '预测值',
        type: chartType.value === 'bar' ? 'bar' : 'line',
        smooth: true,
        data: data.predicted,
        itemStyle: { color: '#0EA5E9' },
        lineStyle: { width: 3 },
        symbol: 'circle',
        symbolSize: 5,
        areaStyle:
          chartType.value === 'area'
            ? {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: 'rgba(14,165,233,0.2)' },
                  { offset: 1, color: 'rgba(14,165,233,0)' }
                ])
              }
            : undefined
      }
    ]
    if (data.supply_forecast) {
      series.push({
        name: '供应能力',
        type: 'line',
        smooth: true,
        data: data.supply_forecast,
        itemStyle: { color: '#10B981' },
        lineStyle: { width: 2, type: 'dashed' }
      })
    }
    chart.setOption({
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(255,255,255,0.95)',
        borderColor: '#E2E8F0',
        textStyle: { color: '#0F172A', fontSize: 12 }
      },
      legend: { bottom: 0, textStyle: { color: '#64748B', fontSize: 12 } },
      grid: { left: '2%', right: '4%', top: '4%', bottom: '12%', containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: data.dates,
        axisLine: { lineStyle: { color: '#E2E8F0' } },
        axisLabel: { color: '#94A3B8', fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#F1F5F9' } },
        axisLabel: { color: '#94A3B8', fontSize: 11 }
      },
      series
    })
  }
  ;(chartRef.value as any)._echarts = chart
}

// === AI 对话 ===
const stopStreaming = () => {
  const controller = abortControllers.get(activeChatId.value)
  if (controller) controller.abort()
}

const chatMemory = new Map<string, ChatMessage[]>()
const clarificationDraft = reactive<Record<string, Record<string, any>>>({})
/** 当前智能体右侧面板正在使用的服务端会话；切换业务对象不会改变它。 */
const activeConversationNo = ref<string | null>(null)
const hasMoreHistoryMessages = ref(false)
const loadingOlderMessages = ref(false)
const oldestHistoryMessageId = ref<number | null>(null)

const conversationMessage = (item: any): ChatMessage => ({
  id: String(item.id),
  role: item.role,
  content: item.role === 'assistant' ? renderMarkdown(item.content) : item.content,
  clarification: item.clarification || undefined,
  clarificationResolved: Boolean(item.clarification),
  presentation: item.presentation || item.resultData?.presentation || undefined,
  executionResult: item.executionResult || item.execution_result || item.resultData?.executionResult || undefined,
  taskNo: item.taskNo || (item.references || []).find((reference: any) => reference.referenceType === 'EXECUTION_TASK')?.referenceNo,
  time: item.createdAt ? new Date(item.createdAt).toLocaleTimeString() : new Date().toLocaleTimeString()
})

/** 页面重新打开时恢复当前智能体最近的会话，而不是按预测批次替换聊天记录。 */
const restoreLatestAgentConversation = async (targetAgentCode = agentId.value) => {
  try {
    const listResponse = await getJson<any[]>('/ai/conversations')
    const latest = (listResponse.data || []).find(
      (item: any) => item.agentCode === targetAgentCode && item.status === 'ACTIVE'
    )
    if (!latest || agentId.value !== targetAgentCode) return
    const detailResponse = await getJson<any>(
      `/ai/conversations/detail?conversationNo=${encodeURIComponent(latest.conversationNo)}&pageSize=20`
    )
    const detail = detailResponse.data
    if (!detail || agentId.value !== targetAgentCode) return
    activeConversationNo.value = detail.conversationNo || null
    if (activeConversationNo.value) {
      conversationMemory.set(targetAgentCode, activeConversationNo.value)
    }
    if (Array.isArray(detail.messages) && detail.messages.length) {
      messages.value = detail.messages.map(conversationMessage)
      hasMoreHistoryMessages.value = Boolean(detail.hasMoreMessages)
      oldestHistoryMessageId.value = Number(detail.messages[0]?.id) || null
      const latestMessage = messages.value[messages.value.length - 1]
      if (latestMessage?.role === 'assistant' && latestMessage.clarification) {
        latestMessage.clarificationResolved = false
        clarificationDraft[latestMessage.id] = {}
      }
      saveChatToMemory(activeChatId.value)
      scrollToBottom()
    }
  } catch (error) {
    console.error('恢复智能体最近会话失败:', error)
  }
}

const loadOlderMessagesOnScroll = async () => {
  const container = messagesRef.value
  if (!container || container.scrollTop > 24 || loadingOlderMessages.value || !hasMoreHistoryMessages.value
      || !activeConversationNo.value || !oldestHistoryMessageId.value) return
  loadingOlderMessages.value = true
  const previousHeight = container.scrollHeight
  try {
    const response = await getJson<any>(`/ai/conversations/detail?conversationNo=${encodeURIComponent(activeConversationNo.value)}&beforeMessageId=${oldestHistoryMessageId.value}&pageSize=20`)
    const detail = response.data
    const older = Array.isArray(detail?.messages) ? detail.messages : []
    if (older.length) {
      messages.value.unshift(...older.map(conversationMessage))
      oldestHistoryMessageId.value = Number(older[0].id) || oldestHistoryMessageId.value
      saveChatToMemory(activeChatId.value)
      await nextTick()
      container.scrollTop += container.scrollHeight - previousHeight
    }
    hasMoreHistoryMessages.value = Boolean(detail?.hasMoreMessages)
  } catch (error) {
    console.error('加载更早会话消息失败:', error)
  } finally {
    loadingOlderMessages.value = false
  }
}

const updateMsgInMemory = (id: string, msgId: string, content: string) => {
  const msgs = chatMemory.get(id)
  const msg = msgs?.find((item) => item.id === msgId)
  if (msg) msg.content = content
}


/**
 * 发送消息时的当前业务焦点。
 *
 * 它可以随左侧选择变化，只帮助Agent理解“当前客户/这个范围”等指代，
 * 不用于选择或切换会话。名称用于确认展示，编码仍由服务端校验后执行。
 */
const activeBusinessContext = () => {
  const source = isShortTerm.value ? shortTermForecastContext() : (currentResult.value || modelResult.value || {})
  return {
    regionCode: source.regionCode || source.region_code || undefined,
    regionName: source.regionName || source.region_name || source.province || undefined,
    industryCode: source.industryCode || source.industry_code || undefined,
    industryName: source.industryName || source.industry_name || source.industry || undefined,
    customerCode: source.customerCode || source.customer_code || undefined,
    customerName: source.customerName || source.customer_name || source.customer || undefined
  }
}

interface AgentStructuredReply {
  source: ChatMessage
  inputType: 'CLARIFICATION_REPLY' | 'INTERACTION_REPLY'
  answers: Record<string, any>
  displayText: string
}

const setAgentClarificationValue = (messageId: string, field: string, value: any) => {
  if (!clarificationDraft[messageId]) clarificationDraft[messageId] = {}
  clarificationDraft[messageId][field] = value
}
const setAgentClarificationEvent = (messageId: string, field: string, event: Event) => {
  setAgentClarificationValue(messageId, field, (event.target as HTMLInputElement | HTMLSelectElement)?.value)
}
const replyClarification = (
  message: ChatMessage,
  directAnswers?: Record<string, any>,
  directLabel?: string
) => {
  const clarification = message.clarification
  if (!clarification) return
  const answers = directAnswers || { ...(clarificationDraft[message.id] || {}) }
  const missing = clarification.fields.filter((field) => field.required && field.inputType !== 'CONFIRM'
    && (answers[field.parameterCode] === undefined || answers[field.parameterCode] === ''))
  if (missing.length) {
    ElMessage.warning(`请先填写：${missing.map((field) => field.parameterName).join('、')}`)
    return
  }
  const displayText = directLabel || clarification.fields.map((field) => {
    const value = answers[field.parameterCode]
    return field.options.find((option) => option.value === value)?.label || String(value || '')
  }).filter(Boolean).join('；') || '补充信息'
  void sendMessage({ source: message, inputType: 'CLARIFICATION_REPLY', answers, displayText })
}
const sendMessage = async (structured?: AgentStructuredReply) => {
  // 用户始终通过同一个输入框对话。若上一轮正在等补充信息，则把这句自然语言
  // 自动作为该问题的回答提交，页面不再显示下拉框、表单或确认按钮。
  // 只允许最后一条助手消息等待回答，历史遗留的澄清不能劫持新的普通对话。
  const latestMessage = messages.value[messages.value.length - 1]
  const pending = latestMessage?.role === 'assistant' && latestMessage.clarification
    && !latestMessage.clarificationResolved ? latestMessage : undefined
  const effectiveReply = structured || (pending ? {
    source: pending,
    inputType: pending.clarification!.interactionType === 'CONFIRMATION'
      ? 'INTERACTION_REPLY'
      : 'CLARIFICATION_REPLY',
    answers: {},
    displayText: inputMessage.value.trim()
  } as AgentStructuredReply : undefined)
  if ((!effectiveReply && !inputMessage.value.trim()) || isStreaming.value) return
  const userMessage = effectiveReply?.displayText || inputMessage.value
  if (!structured) inputMessage.value = ''
  if (effectiveReply) effectiveReply.source.clarificationResolved = true
  const streamId = activeChatId.value
  messages.value.push({
    id: Date.now().toString(),
    role: 'user',
    content: userMessage,
    time: new Date().toLocaleTimeString()
  })
  scrollToBottom()
  streamingSet.add(streamId)
  const msgId = (Date.now() + 1).toString()
  messages.value.push({ id: msgId, role: 'assistant', content: '', time: new Date().toLocaleTimeString() })
  saveChatToMemory(streamId)

  const controller = new AbortController()
  abortControllers.set(streamId, controller)
  let fullResponse = ''
  let staleInteraction = false
  const expiredInteraction = (error: unknown) => {
    const message = error instanceof Error ? error.message : String(error || '')
    return /当前会话没有等待处理|澄清交互已过期|回复类型与当前待处理交互不匹配/.test(message)
  }
  const requestBody = (reply?: AgentStructuredReply) => ({
    conversationNo: activeConversationNo.value || conversationMemory.get(streamId) || null,
    message: userMessage,
    inputType: reply?.inputType || 'TEXT',
    interactionId: reply?.source.clarification?.interactionId || null,
    replyToTurnId: reply?.source.clarification?.replyToTurnId || null,
    agentCode: agentId.value,
    businessContext: activeBusinessContext()
  })
  const requestStream = (reply?: AgentStructuredReply) => postSse<any>(
    '/ai/conversations/chat/stream', requestBody(reply),
    (event, value) => {
      if (activeChatId.value !== streamId) return
      const msg = messages.value.find((item) => item.id === msgId)
      if (!msg) return
      if (event === 'progress') {
        msg.content = renderMarkdown(String(value))
      } else if (event === 'delta') {
        fullResponse += String(value)
        msg.content = renderMarkdown(fullResponse)
      }
      scrollToBottom()
    }, controller.signal
  )

  try {
    let data: any
    try {
      data = await requestStream(effectiveReply)
    } catch (error) {
      // Java 是 pendingOperation 的唯一权威。过期澄清在 Java 校验阶段被拒绝，
      // 尚未创建用户消息或任务，因此可安全地用同一段文字按 TEXT 重发。
      if (effectiveReply?.inputType !== 'CLARIFICATION_REPLY' || !expiredInteraction(error)) throw error
      staleInteraction = true
      effectiveReply.source.clarificationResolved = true
      fullResponse = ''
      data = await requestStream()
    }
    if (data.conversationNo) {
      conversationMemory.set(streamId, data.conversationNo)
      activeConversationNo.value = data.conversationNo
    }
    fullResponse = data.message || 'AI 未返回有效内容'
    if (activeChatId.value === streamId) {
      const msg = messages.value.find((item) => item.id === msgId)
      if (msg) {
        msg.content = renderMarkdown(fullResponse)
        msg.clarification = data.clarification || undefined
        msg.presentation = data.presentation || data.resultData?.presentation || undefined
        msg.executionResult = data.executionResult || data.execution_result || data.resultData?.executionResult || undefined
        msg.taskNo = data.taskNo || (data.references || []).find((item: any) => item.referenceType === 'EXECUTION_TASK')?.referenceNo
        if (msg.clarification) clarificationDraft[msg.id] = {}
      }
      saveChatToMemory(streamId)
      scrollToBottom()
      window.setTimeout(scrollToBottom, 120)
      window.setTimeout(scrollToBottom, 500)
    } else {
      updateMsgInMemory(streamId, msgId, renderMarkdown(fullResponse))
    }

    // 执行进度统一由执行任务卡片按 taskNo 展示；这里不再根据训练/预测批次轮询旧接口。
  } catch (e: any) {
    if (effectiveReply && !staleInteraction) effectiveReply.source.clarificationResolved = false
    if (e.name === 'AbortError') {
      const content = renderMarkdown(fullResponse || '（已停止）')
      if (activeChatId.value === streamId) {
        const msg = messages.value.find((m) => m.id === msgId)
        if (msg) msg.content = content
        saveChatToMemory(streamId)
      } else {
        updateMsgInMemory(streamId, msgId, content)
      }
    } else {
      const errorMessage = e instanceof Error && e.message ? e.message : '网络错误'
      if (activeChatId.value === streamId) {
        const msg = messages.value.find((m) => m.id === msgId)
        if (msg) msg.content = renderMarkdown(`⚠️ ${errorMessage}`)
        saveChatToMemory(streamId)
      } else {
        updateMsgInMemory(streamId, msgId, renderMarkdown(`⚠️ ${errorMessage}`))
      }
    }
  } finally {
    streamingSet.delete(streamId)
    abortControllers.delete(streamId)
    if (activeChatId.value === streamId) scrollToBottom()
  }
}

const sendAnalysisMessage = async (analysisText: string) => {
  const streamId = activeChatId.value
  streamingSet.add(streamId)
  const msgId = Date.now().toString()
  messages.value.push({ id: msgId, role: 'assistant', content: '', time: new Date().toLocaleTimeString() })
  try {
    const msg = messages.value.find((m) => m.id === msgId)
    if (!msg) return
    if (analysisText.length > 20) {
      const step = Math.max(1, Math.ceil(analysisText.length / 40))
      for (let i = step; i < analysisText.length; i += step) {
        await new Promise((r) => setTimeout(r, 12))
        msg.content = renderMarkdown(analysisText.substring(0, i))
        if (activeChatId.value === streamId) scrollToBottom()
      }
    }
    msg.content = renderMarkdown(analysisText || '分析已完成。')
  } finally {
    streamingSet.delete(streamId)
    saveChatToMemory(streamId)
    if (activeChatId.value === streamId) scrollToBottom()
  }
}

const clearChat = async () => {
  try {
    if (activeConversationNo.value) {
      await getJson(`/ai/conversations/delete?conversationNo=${encodeURIComponent(activeConversationNo.value)}`)
      conversationMemory.delete(activeChatId.value)
      activeConversationNo.value = null
    }
    messages.value = []
    saveChatToMemory(activeChatId.value)
    messages.value = [{
      id: Date.now().toString(),
      role: 'assistant',
      content: renderMarkdown('对话已清空，请随时提问！'),
      time: new Date().toLocaleTimeString()
    }]
    saveChatToMemory(activeChatId.value)
    ElMessage.success('会话已清空')
  } catch (error) {
    const message = error instanceof Error ? error.message : '清空会话失败'
    ElMessage.error(message)
  }
}

const saveChatToMemory = (id: string) => {
  chatMemory.set(id, messages.value.map((message) => ({ ...message })))
}

const loadChatFromMemory = (id: string): boolean => {
  const saved = chatMemory.get(id)
  if (!saved) return false
  messages.value = saved.map((message) => ({ ...message }))
  return true
}

const getGreeting = (id: string): string => {
  if (id === 'monthly-sales')
    return '您好！我是月度销量预测分析助手。\n\n请在左侧选择省份和行业，查看12个预测模型的结果。\n\n您可以随时向我提问关于预测趋势、模型精度、行业对比等方面的问题。'
  if (id.startsWith('short-term'))
    return '您好！我是短期客户预测分析助手。\n\n请在左侧选择行业和客户，查看短期用气量预测结果。\n\n城市燃气行业支持按客户查看预测，其他行业展示整体预测数据。'
  if (id === 'winter-supply')
    return '您好！我是冬季保供预测分析助手。\n\n请在左侧选择省份，查看冬供旬度预测结果。\n\n您可以随时向我提问关于冬供趋势、模型精度、供需分析等方面的问题。'
  return '您好！我是智能分析助手。\n\n请在左侧完成数据选择和模型配置，运行预测后我将为您详细分析结果。\n\n您可以随时向我提问关于预测趋势、异常分析、业务建议等方面的问题。'
}

const initOrRestoreChat = (id: string) => {
  inputMessage.value = ''
  hasMoreHistoryMessages.value = false
  oldestHistoryMessageId.value = null
  if (!loadChatFromMemory(id)) {
    messages.value = [
      { id: '1', role: 'assistant', content: renderMarkdown(getGreeting(id)), time: new Date().toLocaleTimeString() }
    ]
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    const container = messagesRef.value
    if (!container) return
    requestAnimationFrame(() => {
      container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' })
    })
  })
}

const messages = ref<ChatMessage[]>([
  {
    id: '1',
    role: 'assistant',
    content: renderMarkdown(
      '您好！我是智能分析助手。\n\n请在左侧完成数据选择和模型配置，运行预测后我将为您详细分析结果。\n\n您可以随时向我提问关于预测趋势、异常分析、业务建议等方面的问题。'
    ),
    time: new Date().toLocaleTimeString()
  }
])

/** 会话恢复完成后再加载左侧，保证当前会话的最后一次预测优先。 */
const loadCurrentAgentResult = async () => {
  if (isMonthlySales.value) {
    await loadProvinceMape()
    await loadMonthlyResult()
  } else if (isShortTerm.value) {
    await loadShortTermDashboard()
  } else if (isWinterSupply.value) {
    await loadWsMape()
    await loadWsResult()
  } else {
    await loadAgentConfig()
  }
}

let resizeHandler: (() => void) | null = null
onMounted(() => {
  activeChatId.value = agentId.value
  initOrRestoreChat(activeChatId.value)
  void (async () => {
    await restoreLatestAgentConversation(agentId.value)
    await loadCurrentAgentResult()
  })()
  resizeHandler = () => {
    ;[chartRef, chartRefTop, chartRefBottom].forEach((ref) => {
      if (ref.value && (ref.value as any)._echarts) (ref.value as any)._echarts.resize()
    })
  }
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  ;[chartRef, chartRefTop, chartRefBottom].forEach((ref) => {
    if (ref.value) echarts.getInstanceByDom(ref.value)?.dispose()
  })
})

// 切换智能体时重新加载
watch(agentId, (newId, oldId) => {
  if (newId === oldId) return
  saveChatToMemory(activeChatId.value)
  activeChatId.value = newId
  activeConversationNo.value = conversationMemory.get(newId) || null
  modelResult.value = null
  currentResult.value = null
  initOrRestoreChat(activeChatId.value)
  if (isShortTerm.value) {
    selectedStProvince.value = ''
    selectedStIndustry.value = ''
    selectedStCustomer.value = ''
  } else if (isWinterSupply.value) {
    selectedWsProvince.value = '江苏'
  }
  void (async () => {
    await restoreLatestAgentConversation(newId)
    await loadCurrentAgentResult()
  })()
})

// 图表类型切换时重绘
watch(chartType, () => {
  if (currentResult.value && (isMonthlySales.value || isShortTerm.value || isWinterSupply.value)) {
    nextTick(() => initMonthlyChart(currentResult.value))
  } else if (modelResult.value) {
    nextTick(() => initChart(modelResult.value))
  }
})

watch(futureChartType, () => {
  if (currentResult.value && (isMonthlySales.value || isShortTerm.value || isWinterSupply.value)) {
    nextTick(() => initMonthlyChart(currentResult.value))
  }
})

</script>

<style scoped>
.agent-detail {
  display: flex;
  height: 100%;
  min-height: 520px;
  overflow: hidden;
}
.agent-detail.dragging {
  cursor: col-resize;
  user-select: none;
}

/* 左侧 */
.workspace {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: white;
  border: 1px solid #f1f5f9;
  border-radius: 16px;
  overflow: hidden;
}
.agent-workspace-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  flex: 0 0 auto;
  padding: 12px;
  border-bottom: 1px solid #f1f5f9;
  background: #f8fafc;
}
.agent-workspace-tabs a {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 11px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  background: #fff;
  color: #475569;
  text-decoration: none;
}
.agent-workspace-tabs a.active {
  border-color: #38bdf8;
  background: #f0f9ff;
  color: #0284c7;
  box-shadow: 0 3px 10px rgba(14, 165, 233, 0.12);
}
.agent-workspace-tabs a > span:last-child { min-width: 0; }
.agent-workspace-tabs strong,
.agent-workspace-tabs small { display: block; }
.agent-workspace-tabs strong { color: inherit; font-size: 13px; font-weight: 650; }
.agent-workspace-tabs small { margin-top: 2px; overflow: hidden; color: #94a3b8; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.agent-tab-icon { width: 30px; height: 30px; display: grid; place-items: center; flex: 0 0 30px; border-radius: 8px; }
.agent-tab-icon svg { width: 16px; height: 16px; }
.agent-tab-icon.winter-supply { background: #e0f2fe; color: #0284c7; }
.agent-tab-icon.monthly-sales { background: #d1fae5; color: #059669; }
.agent-tab-icon.short-term { background: #fef3c7; color: #d97706; }
.workspace-scroll {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 32px;
}

/* 区块 */
.section-block {
  padding-bottom: 28px;
  margin-bottom: 28px;
  border-bottom: 1px solid #f1f5f9;
}
.section-block:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
}
.section-head-right {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  width: 100%;
  gap: 16px;
}
.section-badge {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
  color: white;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
  letter-spacing: 0;
  margin-bottom: 3px;
}
.section-title .el-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: #e0f2fe;
  color: #0284c7;
  font-size: 17px;
  flex: 0 0 auto;
}
.section-block:nth-of-type(4n + 1) .section-title .el-icon {
  background: #e0f2fe;
  color: #0284c7;
}
.section-block:nth-of-type(4n + 2) .section-title .el-icon {
  background: #dcfce7;
  color: #16a34a;
}
.section-block:nth-of-type(4n + 3) .section-title .el-icon {
  background: #fef3c7;
  color: #d97706;
}
.section-block:nth-of-type(4n) .section-title .el-icon {
  background: #f3e8ff;
  color: #7c3aed;
}
.section-hint {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}
.refresh-result-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  flex: 0 0 auto;
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  background: #f0f9ff;
  color: #0284c7;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s;
}
.refresh-result-btn:hover:not(:disabled) {
  border-color: #0ea5e9;
  background: #e0f2fe;
  box-shadow: 0 4px 12px -6px rgba(14, 165, 233, 0.45);
}
.refresh-result-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.refresh-result-btn svg {
  width: 15px;
  height: 15px;
  flex: 0 0 auto;
}
.mini-spinner {
  width: 13px;
  height: 13px;
  border: 2px solid rgba(2, 132, 199, 0.22);
  border-top-color: #0284c7;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* 表单 */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-field.full {
  grid-column: 1 / -1;
}
.form-field label {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}
.field-input {
  width: 100%;
}

/* 省份选择器 */
.region-selector {
  display: flex;
  gap: 8px;
}
.region-btn {
  padding: 10px 24px;
  border: 1.5px solid #e2e8f0;
  background: white;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}
.region-btn:hover {
  border-color: #7dd3fc;
  background: #f0f9ff;
}
.region-btn.active {
  border-color: #0ea5e9;
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
  color: white;
  box-shadow: 0 4px 12px -2px rgba(14, 165, 233, 0.3);
}
.region-mape {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.2);
}
.region-btn:not(.active) .region-mape {
  background: #f1f5f9;
  color: #64748b;
}

/* 行业下拉选择 */
.industry-select-wrap {
  display: flex;
  align-items: center;
  gap: 16px;
}
.industry-select-current {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.industry-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}
.industry-select-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.industry-select-name {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}
.industry-select-mape {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}
.industry-select {
  flex: 1;
}
.industry-select :deep(.el-select__wrapper) {
  border-radius: 10px;
  border: 1.5px solid #e2e8f0;
  box-shadow: none;
  padding: 10px 14px;
  background: #fafbfc;
  transition: border-color 0.2s;
}
.industry-select :deep(.el-select__wrapper:hover) {
  border-color: #7dd3fc;
}
.industry-select :deep(.el-select__wrapper.is-focused) {
  border-color: #0ea5e9;
  box-shadow: 0 0 0 2px rgba(14, 165, 233, 0.1);
}
.industry-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}
.industry-option-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}
.industry-option-name {
  font-size: 14px;
  font-weight: 500;
  color: #0f172a;
  flex: 1;
}
.industry-option-mape {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}

/* 短期客户预测看板 */
.short-term-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}
.short-term-hero h1 {
  margin: 0;
  color: #101828;
  font-size: 20px;
  font-weight: 600;
}
.short-term-hero p {
  margin: 5px 0 0;
  color: #667085;
  font-size: 13px;
  font-weight: 400;
}
.compact-section {
  margin-bottom: 12px;
  padding: 14px 16px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(31, 64, 104, 0.035);
}
.compact-section .section-head {
  gap: 10px;
  margin-bottom: 12px;
}
.compact-section .section-badge {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
  font-size: 13px;
}
.compact-section .section-title {
  margin: 0 0 2px;
  color: #1f2937;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.1px;
}
.compact-section .section-hint {
  color: #667085;
  font-size: 13px;
  font-weight: 500;
}
.short-term-filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  align-items: end;
  gap: 14px;
  padding-left: 38px;
}
.short-term-filter-grid :deep(.el-select__wrapper) {
  min-height: 36px;
  border-radius: 6px;
}
.short-term-filter-grid .form-field label {
  color: #344054;
  font-family: var(--app-font-family);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}
.short-term-filter-grid :deep(.el-select__selected-item),
.short-term-filter-grid :deep(.el-select__placeholder) {
  font-family: var(--app-font-family);
  font-size: 14px;
  font-weight: 500;
}
.short-term-filter-grid :deep(.el-select__selected-item) {
  color: #101828;
}
.short-term-filter-grid :deep(.el-select__placeholder.is-transparent) {
  color: #98a2b3;
}
.short-term-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 1px;
}
.short-term-filter-actions :deep(.el-button) {
  min-width: 68px;
  height: 36px;
  margin-left: 0;
  border-radius: 6px;
}
.short-term-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding-left: 38px;
}
.short-term-summary-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 12px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #f8fafc;
}
.short-term-summary-card > .el-icon {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: 8px;
  background: #eaf3ff;
  color: #0ea5e9;
  font-size: 17px;
}
.short-term-summary-card:nth-child(2) > .el-icon {
  background: #ecfdf3;
  color: #12b76a;
}
.short-term-summary-card:nth-child(3) > .el-icon {
  background: #fff4e5;
  color: #f79009;
}
.short-term-summary-card.trend-up > .el-icon {
  background: #fff1f0;
  color: #f04438;
}
.short-term-summary-card.trend-up strong {
  color: #f04438;
}
.short-term-summary-card.trend-down > .el-icon {
  background: #ecfdf3;
  color: #12b76a;
}
.short-term-summary-card.trend-down strong {
  color: #12b76a;
}
.short-term-summary-card:nth-child(4) > .el-icon {
  background: #f4f3ff;
  color: #7f56d9;
}
.short-term-summary-card div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}
.short-term-summary-card span {
  color: #667085;
  font-size: 13px;
  font-weight: 500;
}
.short-term-summary-card strong {
  overflow: hidden;
  color: #101828;
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.result-scope-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 10px 38px;
  padding: 8px 12px;
  border: 1px solid #e8eef7;
  border-radius: 8px;
  background: #f8faff;
  color: #667085;
  font-size: 12px;
}
.result-scope-bar strong {
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}
.result-scope-bar em {
  margin-left: auto;
  color: #2f6fed;
  font-style: normal;
}
.future-comparison-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  margin: 4px 0 14px 38px;
}
.future-comparison-summary > div {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 2px 0;
}
.future-comparison-summary > div > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}
.future-comparison-summary span { color: #667085; font-size: 12px; }
.future-comparison-summary strong { color: #344054; font-size: 18px; line-height: 1.15; }
.future-comparison-summary small { overflow: hidden; color: #667085; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.future-comparison-summary .comparison-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #eff8ff;
  color: #2e90fa;
  font-size: 14px;
}
.future-comparison-summary .forecast-total .comparison-icon { background: #eef4ff; color: #2f6fed; }
.future-comparison-summary .forecast-total strong { color: #2f6fed; }
.future-comparison-summary > div:last-child .comparison-icon { background: #ecfdf3; color: #12b76a; }
.future-comparison-summary .trend-up strong { color: #f04438; }
.future-comparison-summary .trend-down strong { color: #12b76a; }
.short-term-future-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}
.compact-chart-panel {
  min-height: 290px;
  padding: 8px 10px 4px;
  border-color: #edf1f7;
  border-radius: 8px;
}
.compact-chart-panel .chart {
  height: 276px;
}
.future-table-wrap {
  overflow: hidden;
  border: 1px solid #edf1f7;
  border-radius: 8px;
}
.future-table-wrap :deep(.el-table th.el-table__cell) {
  background: #f8fafc;
  color: #475467;
  font-weight: 600;
}
.future-table-wrap :deep(.el-table .cell) {
  padding: 0 8px;
  text-align: center;
}
.future-table-wrap :deep(.nowrap-cell .cell) {
  white-space: nowrap;
}
.future-table-wrap .daily-up {
  color: #f04438;
  font-weight: 600;
}
.future-table-wrap .daily-down {
  color: #12b76a;
  font-weight: 600;
}
.forecast-meta-info {
  display: flex;
  gap: 18px;
  margin-top: 10px;
  color: #667085;
  font-size: 14px;
  line-height: 1.6;
}
.forecast-analysis {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #eef2f6;
}
.forecast-analysis strong { color: #1e3a5f; font-size: 14px; }
.forecast-analysis p { margin: 7px 0 0; color: #475467; font-size: 14px; line-height: 1.75; }

/* 模型信息卡片 */
.model-info-card {
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 14px;
  padding: 20px;
}
.model-info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.model-info-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
  letter-spacing: 0;
}
.model-info-name .el-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, #e0f2fe, #dcfce7);
  color: #0284c7;
  font-size: 17px;
  flex: 0 0 auto;
}
.model-info-desc {
  font-size: 13px;
  color: #64748b;
  margin-top: 6px;
  font-weight: 500;
}
.model-info-tag {
  padding: 4px 12px;
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
  color: white;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
}
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 12px 16px;
  background: white;
  border-radius: 10px;
  border: 1px solid #f1f5f9;
}
.metric-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: #f0f9ff;
  color: #0284c7;
  font-size: 17px;
}
.metric-item:nth-child(1) .metric-icon {
  background: #dcfce7;
  color: #16a34a;
}
.metric-item:nth-child(2) .metric-icon {
  background: #e0f2fe;
  color: #0284c7;
}
.metric-item:nth-child(3) .metric-icon {
  background: #fef3c7;
  color: #d97706;
}
.metric-item:nth-child(4) .metric-icon {
  background: #f3e8ff;
  color: #7c3aed;
}
.metric-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0;
}
.metric-value {
  font-size: 24px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.1;
}
.metric-value.metric-good {
  color: #10b981;
}
.metric-value.metric-ok {
  color: #f59e0b;
}
.metric-value.metric-bad {
  color: #ef4444;
}
.short-term-model-card {
  padding: 12px;
  border-radius: 10px;
}
.short-term-model-card .model-info-header {
  margin-bottom: 10px;
}
.short-term-model-card .model-info-name {
  font-size: 15px;
  font-weight: 700;
}
.short-term-model-card .model-info-tag {
  padding: 3px 9px;
  font-size: 10px;
}
.short-term-model-card .metrics-grid {
  gap: 8px;
}
.short-term-model-card .metric-item {
  gap: 4px;
  padding: 8px 10px;
}
.short-term-model-card .metric-label {
  font-size: 11px;
  letter-spacing: 0.05em;
}
.short-term-model-card .compact-metric-label {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  gap: 5px;
}
.short-term-model-card .metric-icon {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  font-size: 11px;
}
.short-term-model-card .metric-value {
  font-size: 18px;
  font-weight: 700;
}

/* 数据摘要 */
.data-summary {
  display: flex;
  gap: 16px;
  margin-top: 16px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 10px;
}
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.summary-label {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 600;
}
.summary-value {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

/* 加载中 */
.loading-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px;
}
.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #f1f5f9;
  border-top-color: #0ea5e9;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.loading-text {
  font-size: 14px;
  color: #94a3b8;
}

/* 模型选择(通用) */
.model-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 20px;
}
.model-item {
  position: relative;
  padding: 16px;
  border: 1.5px solid #f1f5f9;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.model-item:hover:not(.disabled) {
  border-color: #7dd3fc;
  background: #f0f9ff;
}
.model-item.selected {
  border-color: #0ea5e9;
  background: #f0f9ff;
}
.model-item.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.model-check {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 18px;
  height: 18px;
  background: #0ea5e9;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.model-check svg {
  width: 12px;
  height: 12px;
}
.model-name {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 2px;
}
.model-desc {
  font-size: 13px;
  color: #94a3b8;
}

.param-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 12px;
}
.param-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.param-label {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  min-width: 70px;
}
.param-control {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}
.slider {
  flex: 1;
}
.param-val {
  font-size: 13px;
  font-weight: 600;
  color: #0ea5e9;
  min-width: 50px;
  text-align: right;
}

/* 运行 */
.run-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}
.run-btn {
  padding: 14px 40px;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
  color: white;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 8px;
}
.run-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px -6px rgba(14, 165, 233, 0.4);
}
.run-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.run-btn-text {
  display: flex;
  align-items: center;
  gap: 8px;
}
.play-icon {
  width: 18px;
  height: 18px;
}
.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.progress-tracker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-width: 400px;
}
.tracker-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.3s;
}
.tracker-item.done {
  color: #10b981;
}
.tracker-item.active {
  background: #f0f9ff;
  color: #0ea5e9;
}
.tracker-item.pending {
  color: #cbd5e1;
}
.tracker-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid currentColor;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.3s;
}
.tracker-item.done .tracker-dot {
  background: #10b981;
  border-color: #10b981;
}
.tracker-dot svg {
  width: 12px;
  height: 12px;
  color: white;
}
.dot-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.4s ease-in-out infinite;
}
@keyframes pulse {
  0%,
  100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.2);
    opacity: 1;
  }
}
.tracker-text {
  font-size: 13px;
  font-weight: 500;
}

.result-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  width: 100%;
  max-width: 500px;
}
.result-card {
  padding: 16px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 12px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.result-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: white;
  color: #0284c7;
  font-size: 17px;
  box-shadow: 0 1px 2px rgba(14, 165, 233, 0.12);
}
.result-card:nth-child(1) .result-icon {
  background: #dcfce7;
  color: #16a34a;
}
.result-card:nth-child(2) .result-icon {
  background: #e0f2fe;
  color: #0284c7;
}
.result-card:nth-child(3) .result-icon {
  background: #f3e8ff;
  color: #7c3aed;
}
.result-label {
  font-size: 13px;
  color: #0284c7;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0;
}
.result-value {
  font-size: 25px;
  font-weight: 600;
  color: #0f172a;
  letter-spacing: 0;
  line-height: 1.1;
}
.result-value small {
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
}

/* 图表 */
.chart-toggle {
  display: flex;
  gap: 2px;
  background: #f1f5f9;
  padding: 3px;
  border-radius: 8px;
}
.toggle-btn {
  padding: 6px 14px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}
.toggle-btn.active {
  background: white;
  color: #0ea5e9;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.chart-box {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #fafbfc;
  border-radius: 12px;
  padding: 16px;
}
.result-chart-panel {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  min-height: 330px;
  padding: 12px 14px 6px;
  border: 1px solid #edf1f7;
  border-radius: 10px;
  background: #fff;
}
.chart-label {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
  margin-top: 12px;
}
.chart-label:first-child {
  margin-top: 0;
}
.chart {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  height: 100%;
  overflow: hidden;
}

/* 拖动分隔条 */
.splitter {
  flex: 0 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: col-resize;
  position: relative;
  z-index: 10;
}
.splitter-line {
  width: 4px;
  height: 100%;
  border-radius: 2px;
  background: transparent;
  transition: background 0.2s;
}
.splitter:hover .splitter-line,
.agent-detail:has(.splitter:active) .splitter-line {
  background: #0ea5e9;
}

/* 右侧AI */
.ai-panel {
  flex: 1 1 0;
  min-width: 0;
  min-height: 0;
  background: white;
  border: 1px solid #f1f5f9;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.ai-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
}
.ai-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ai-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.agent-switch-button {
  height: 32px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 11px;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  background: #f0f9ff;
  color: #0284c7;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.agent-switch-button:hover {
  border-color: #7dd3fc;
  background: #e0f2fe;
}
.ai-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}
.ai-avatar svg {
  width: 20px;
  height: 20px;
}
.ai-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}
.ai-context-batch {
  max-width: 230px;
  overflow: hidden;
  color: #64748b;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-status {
  font-size: 13px;
  color: #10b981;
  display: flex;
  align-items: center;
  gap: 4px;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}
.ai-clear {
  padding: 5px 12px;
  border: 1px solid #e2e8f0;
  background: white;
  border-radius: 6px;
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}
.ai-clear:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.ai-messages {
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.msg {
  display: flex;
  gap: 10px;
  min-width: 0;
}
.msg.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
}
.msg-avatar.assistant {
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  color: white;
}
.msg-avatar.user {
  background: #f1f5f9;
  color: #475569;
}
.msg-avatar svg {
  width: 16px;
  height: 16px;
}
.msg-body {
  min-width: 0;
  max-width: 80%;
}
.msg-body.has-task-card {
  width: 100%;
  max-width: 100%;
}
.msg.task-message .msg-avatar {
  display: none;
}
.msg.task-message .msg-body {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}
.task-execution-block {
  display: block;
  width: 100%;
  min-width: 0;
  max-width: 100%;
}
.msg-bubble {
  padding: 12px 15px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
}
.msg-bubble-plain {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.msg.assistant .msg-bubble {
  background: #f8fafc;
  color: #1e293b;
  border-bottom-left-radius: 4px;
}
.msg.user .msg-bubble {
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
  color: white;
  border-bottom-right-radius: 4px;
}
.chat-result-list {
  margin-top: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}
.chat-result-list-head {
  display: flex;
  justify-content: space-between;
  padding: 9px 12px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid #e2e8f0;
}
.chat-result-list-head span { color: #64748b; font-weight: 400; }
.msg-clarification {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  padding: 10px;
  border: 1px solid #bae6fd;
  border-radius: 10px;
  background: #f0f9ff;
}
.msg-clarification-field {
  display: grid;
  flex: 1 1 100%;
  gap: 6px;
}
.msg-clarification-field label { font-size: 14px; font-weight: 600; color: #475569; }
.msg-clarification button,.msg-clarification input,.msg-clarification select {
  min-height: 32px;
  padding: 5px 10px;
  border: 1px solid #bae6fd;
  border-radius: 7px;
  background: white;
  color: #0369a1;
  font-size: 14px;
}
.msg-clarification button { cursor: pointer; }
.msg-clarification button:disabled { cursor: not-allowed; opacity: .55; }
.msg-bubble :deep(h2) {
  font-size: 16px;
  margin: 0 0 8px;
  font-weight: 600;
}
.msg-bubble :deep(h3) {
  font-size: 14px;
  margin: 10px 0 6px;
  font-weight: 600;
}
.msg-bubble :deep(p) {
  margin: 4px 0;
}
.msg-bubble :deep(ul),
.msg-bubble :deep(ol) {
  margin: 6px 0;
  padding-left: 18px;
}
.msg-bubble :deep(li) {
  margin: 3px 0;
}
.msg-bubble :deep(strong) {
  font-weight: 600;
}
.msg-bubble :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 14px;
}
.msg.user .msg-bubble :deep(code) {
  background: rgba(255, 255, 255, 0.2);
}

.msg-bubble.typing {
  display: flex;
  gap: 4px;
  padding: 14px 16px;
}
.msg-bubble.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
  animation: bounce 1.4s infinite ease-in-out;
}
.msg-bubble.typing span:nth-child(2) {
  animation-delay: 0.2s;
}
.msg-bubble.typing span:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

.ai-input {
  flex-shrink: 0;
  padding: 12px 16px 16px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.ai-input :deep(.el-textarea__inner) {
  border-radius: 10px;
  border-color: #e2e8f0;
  font-size: 13px;
  padding: 8px 12px;
  resize: none;
}
.ai-input :deep(.el-textarea__inner:focus) {
  border-color: #0ea5e9;
}
.send-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}
.send-btn:hover:not(:disabled) {
  box-shadow: 0 4px 12px -2px rgba(14, 165, 233, 0.4);
}
.send-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.send-btn svg {
  width: 16px;
  height: 16px;
}
.stop-btn {
  background: linear-gradient(135deg, #ef4444, #dc2626);
}
.stop-btn:hover {
  box-shadow: 0 4px 12px -2px rgba(239, 68, 68, 0.4);
}
.stop-btn svg {
  width: 14px;
  height: 14px;
}

@media (max-width: 1200px) {
  .short-term-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .short-term-future-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .short-term-hero {
    flex-direction: column;
  }
  .short-term-filter-grid,
  .short-term-summary-grid,
  .future-comparison-summary {
    grid-template-columns: 1fr;
    padding-left: 0;
  }
  .future-comparison-summary {
    margin-left: 0;
  }
  .short-term-future-grid {
    padding-left: 0;
  }
}
</style>
