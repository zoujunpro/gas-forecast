<template>
  <div class="agent-detail" :class="{ dragging: isDragging }" :style="{ '--split': splitRatio }">
    <!-- 左侧工作区 -->
    <div class="workspace" :style="{ flexBasis: (splitRatio * 100) + '%' }">
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
                  <h2 class="section-title"><el-icon><DataAnalysis /></el-icon>数据选择</h2>
                  <p class="section-hint">配置数据来源与筛选条件</p>
                </div>
                <button class="refresh-result-btn" :disabled="loadingResult" @click="refreshLatestResult">
                  <svg v-if="!loadingResult" viewBox="0 0 20 20" fill="none">
                    <path d="M16.2 6.5A7 7 0 1 0 17 10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                    <path d="M16.5 3.5V6.8H13.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
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
                <el-date-picker v-model="config.dateRange" type="daterange" range-separator="-" start-placeholder="开始" end-placeholder="结束" class="field-input" disabled />
              </div>
              <div class="form-field full">
                <label>区域选择</label>
                <div class="region-selector">
                  <button v-for="r in monthlyRegions" :key="r.value" class="region-btn" :class="{active: selectedProvince === r.value}" @click="switchProvince(r.value)">
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
                <h2 class="section-title"><el-icon><Grid /></el-icon>行业选择</h2>
                <p class="section-hint">选择行业查看预测结果</p>
              </div>
            </div>
            <div class="industry-select-wrap">
              <div class="industry-select-current" v-if="currentResult">
                <div class="industry-icon" :style="{background: industryColors[selectedIndustry]}">{{ selectedIndustry.charAt(0) }}</div>
                <div class="industry-select-info">
                  <span class="industry-select-name">{{ selectedIndustry }}</span>
                  <span class="industry-select-mape">MAPE {{ currentResult.metrics.mape }}%</span>
                </div>
              </div>
              <el-select v-model="selectedIndustry" placeholder="选择行业" class="industry-select" @change="switchIndustry(selectedIndustry)">
                <el-option v-for="ind in industries" :key="ind" :value="ind" :label="ind">
                  <div class="industry-option">
                    <span class="industry-option-icon" :style="{background: industryColors[ind]}">{{ ind.charAt(0) }}</span>
                    <span class="industry-option-name">{{ ind }}</span>
                    <span class="industry-option-mape" v-if="provinceMape[ind] !== undefined">MAPE {{ provinceMape[ind] }}%</span>
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
                <h2 class="section-title"><el-icon><Cpu /></el-icon>推荐模型</h2>
                <p class="section-hint">基于回测精度自动推荐最优模型</p>
              </div>
            </div>
            <div class="model-info-card">
              <div class="model-info-header">
                <div class="model-info-name"><el-icon><MagicStick /></el-icon>{{ currentResult.model_name }}</div>
                <div class="model-info-tag">最佳模型</div>
              </div>
              <div class="metrics-grid">
                <div class="metric-item">
                  <el-icon class="metric-icon"><TrendCharts /></el-icon>
                  <span class="metric-label">MAPE</span>
                  <span class="metric-value" :class="mapeClass(currentResult.metrics.mape)">{{ currentResult.metrics.mape }}%</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><DataLine /></el-icon>
                  <span class="metric-label">MAE</span>
                  <span class="metric-value">{{ currentResult.metrics.mae }}</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><Histogram /></el-icon>
                  <span class="metric-label">RMSE</span>
                  <span class="metric-value">{{ currentResult.metrics.rmse }}</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><Finished /></el-icon>
                  <span class="metric-label">R²</span>
                  <span class="metric-value">{{ currentResult.metrics.r2 ?? '-' }}</span>
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
                  <h2 class="section-title"><el-icon><PieChart /></el-icon>可视化输出</h2>
                  <p class="section-hint">实际值 vs 预测值对比</p>
                </div>
                <div class="chart-toggle">
                  <button v-for="t in chartTypes" :key="t.key" class="toggle-btn" :class="{active: chartType === t.key}" @click="chartType = t.key">{{ t.label }}</button>
                </div>
              </div>
            </div>
            <div class="chart-box">
              <div class="chart-label">滚动回测结果（实际值 vs 预测值）</div>
              <div ref="chartRefTop" class="chart" style="height: 300px;"></div>
              <div class="chart-label" v-if="currentResult.future_dates">历史与未来预测</div>
              <div ref="chartRefBottom" class="chart" style="height: 300px;" v-if="currentResult.future_dates"></div>
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
          <!-- 01. 区域选择 -->
          <section class="section-block">
            <div class="section-head">
              <span class="section-badge">01</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title"><el-icon><Location /></el-icon>区域选择</h2>
                  <p class="section-hint">选择省份查看短期预测结果</p>
                </div>
                <button class="refresh-result-btn" :disabled="loadingResult" @click="refreshLatestResult">
                  <svg v-if="!loadingResult" viewBox="0 0 20 20" fill="none">
                    <path d="M16.2 6.5A7 7 0 1 0 17 10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                    <path d="M16.5 3.5V6.8H13.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  <span v-else class="mini-spinner"></span>
                  获取最新预测结果
                </button>
              </div>
            </div>
            <div class="region-selector">
              <button v-for="p in stProvinces" :key="p" class="region-btn" :class="{active: selectedStProvince === p}" @click="switchStProvince(p)">
                {{ p }}
              </button>
            </div>
          </section>

          <!-- 02. 行业选择 -->
          <section class="section-block">
            <div class="section-head">
              <span class="section-badge">02</span>
              <div>
                <h2 class="section-title"><el-icon><Grid /></el-icon>行业选择</h2>
                <p class="section-hint">选择行业查看短期预测结果</p>
              </div>
            </div>
            <div class="industry-select-wrap">
              <div class="industry-select-current" v-if="currentResult">
                <div class="industry-icon" :style="{background: stIndustryColors[selectedStIndustry]}">{{ selectedStIndustry.charAt(0) }}</div>
                <div class="industry-select-info">
                  <span class="industry-select-name">{{ selectedStIndustry }}</span>
                  <span class="industry-select-mape">MAPE {{ currentResult.metrics.mape }}%</span>
                </div>
              </div>
              <el-select v-model="selectedStIndustry" placeholder="选择行业" class="industry-select" @change="switchStIndustry(selectedStIndustry)">
                <el-option v-for="ind in stIndustries" :key="ind" :value="ind" :label="ind">
                  <div class="industry-option">
                    <span class="industry-option-icon" :style="{background: stIndustryColors[ind]}">{{ ind.charAt(0) }}</span>
                    <span class="industry-option-name">{{ ind }}</span>
                    <span class="industry-option-mape" v-if="stProvinceMape[ind] !== undefined">MAPE {{ stProvinceMape[ind] }}%</span>
                  </div>
                </el-option>
              </el-select>
            </div>
          </section>

          <!-- 03. 客户选择（仅城市燃气） -->
          <section class="section-block" v-if="stCustomers.length > 0">
            <div class="section-head">
              <span class="section-badge">03</span>
              <div>
                <h2 class="section-title"><el-icon><User /></el-icon>客户选择</h2>
                <p class="section-hint">选择具体客户查看预测（可选）</p>
              </div>
            </div>
            <div class="industry-select-wrap">
              <div class="industry-select-current" v-if="selectedStCustomer">
                <div class="industry-icon" style="background: linear-gradient(135deg, #F59E0B, #D97706)">客</div>
                <div class="industry-select-info">
                  <span class="industry-select-name">{{ selectedStCustomer }}</span>
                  <span class="industry-select-mape" v-if="currentResult">MAPE {{ currentResult.metrics.mape }}%</span>
                </div>
              </div>
              <el-select v-model="selectedStCustomer" placeholder="全部客户（行业整体）" class="industry-select" clearable @change="switchStCustomer(selectedStCustomer)">
                <el-option v-for="c in stCustomers" :key="c" :value="c" :label="c" />
              </el-select>
            </div>
          </section>

          <!-- 04. 模型信息 -->
          <section class="section-block" v-if="currentResult">
            <div class="section-head">
              <span class="section-badge">{{ stCustomers.length > 0 ? '04' : '03' }}</span>
              <div>
                <h2 class="section-title"><el-icon><Cpu /></el-icon>推荐模型</h2>
                <p class="section-hint">基于回测精度自动推荐最优模型</p>
              </div>
            </div>
            <div class="model-info-card">
              <div class="model-info-header">
                <div class="model-info-name"><el-icon><MagicStick /></el-icon>{{ currentResult.model_name }}</div>
                <div class="model-info-tag">最佳模型</div>
              </div>
              <div class="metrics-grid">
                <div class="metric-item">
                  <el-icon class="metric-icon"><TrendCharts /></el-icon>
                  <span class="metric-label">MAPE</span>
                  <span class="metric-value" :class="mapeClass(currentResult.metrics.mape)">{{ currentResult.metrics.mape }}%</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><DataLine /></el-icon>
                  <span class="metric-label">MAE</span>
                  <span class="metric-value">{{ currentResult.metrics.mae }}</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><Histogram /></el-icon>
                  <span class="metric-label">RMSE</span>
                  <span class="metric-value">{{ currentResult.metrics.rmse }}</span>
                </div>
                <div class="metric-item">
                  <el-icon class="metric-icon"><Finished /></el-icon>
                  <span class="metric-label">R²</span>
                  <span class="metric-value">{{ currentResult.metrics.r2 ?? '-' }}</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 05. 可视化输出 -->
          <section class="section-block" v-if="currentResult">
            <div class="section-head">
              <span class="section-badge">{{ stCustomers.length > 0 ? '05' : '04' }}</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title"><el-icon><PieChart /></el-icon>可视化输出</h2>
                  <p class="section-hint">实际值 vs 预测值对比</p>
                </div>
                <div class="chart-toggle">
                  <button v-for="t in chartTypes" :key="t.key" class="toggle-btn" :class="{active: chartType === t.key}" @click="chartType = t.key">{{ t.label }}</button>
                </div>
              </div>
            </div>
            <div class="chart-box">
              <div class="chart-label" v-if="currentResult.dates">滚动回测结果（实际值 vs 预测值）</div>
              <div ref="chartRefTop" class="chart" style="height: 300px;" v-if="currentResult.dates"></div>
              <div class="chart-label" v-if="currentResult.future_dates">历史与未来预测</div>
              <div ref="chartRefBottom" class="chart" style="height: 300px;" v-if="currentResult.future_dates"></div>
            </div>
            <div class="data-summary" v-if="currentResult.future_dates">
              <div class="summary-item" v-if="currentResult.dates">
                <span class="summary-label">回测数据点</span>
                <span class="summary-value">{{ currentResult.dates.length }} 天</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">未来预测</span>
                <span class="summary-value">{{ currentResult.future_dates.length }} 天</span>
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
        <!-- 冬供保供预测专用布局 -->
        <!-- ==================== -->
        <template v-else-if="isWinterSupply">
          <!-- 01. 区域选择 -->
          <section class="section-block">
            <div class="section-head">
              <span class="section-badge">01</span>
              <div class="section-head-right">
                <div>
                  <h2 class="section-title"><el-icon><Location /></el-icon>区域选择</h2>
                  <p class="section-hint">选择省份查看冬供预测结果</p>
                </div>
                <button class="refresh-result-btn" :disabled="loadingResult" @click="refreshLatestResult">
                  <svg v-if="!loadingResult" viewBox="0 0 20 20" fill="none">
                    <path d="M16.2 6.5A7 7 0 1 0 17 10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                    <path d="M16.5 3.5V6.8H13.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  <span v-else class="mini-spinner"></span>
                  获取最新预测结果
                </button>
              </div>
            </div>
            <div class="region-selector">
              <button v-for="p in wsProvinces" :key="p" class="region-btn" :class="{active: selectedWsProvince === p}" @click="switchWsProvince(p)">
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
                <h2 class="section-title"><el-icon><Cpu /></el-icon>推荐模型</h2>
                <p class="section-hint">基于回测精度自动推荐最优模型</p>
              </div>
            </div>
            <div class="model-info-card">
              <div class="model-info-header">
                <div>
                  <div class="model-info-name"><el-icon><MagicStick /></el-icon>{{ currentResult.model_name }}</div>
                  <div class="model-info-desc" v-if="currentResult.formula">{{ currentResult.formula }}</div>
                </div>
                <div class="model-info-tag">最佳模型</div>
              </div>
              <div class="metrics-grid">
                <div class="metric-item">
                  <el-icon class="metric-icon"><TrendCharts /></el-icon>
                  <span class="metric-label">MAPE</span>
                  <span class="metric-value" :class="mapeClass(currentResult.metrics.mape)">{{ currentResult.metrics.mape }}%</span>
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
                  <h2 class="section-title"><el-icon><PieChart /></el-icon>可视化输出</h2>
                  <p class="section-hint">实际值 vs 预测值对比（旬度）</p>
                </div>
                <div class="chart-toggle">
                  <button v-for="t in chartTypes" :key="t.key" class="toggle-btn" :class="{active: chartType === t.key}" @click="chartType = t.key">{{ t.label }}</button>
                </div>
              </div>
            </div>
            <div class="chart-box">
              <div class="chart-label" v-if="currentResult.dates">滚动回测结果（实际值 vs 预测值）</div>
              <div ref="chartRefTop" class="chart" style="height: 300px;" v-if="currentResult.dates"></div>
              <div class="chart-label" v-if="currentResult.future_dates">历史与未来预测</div>
              <div ref="chartRefBottom" class="chart" style="height: 300px;" v-if="currentResult.future_dates"></div>
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
                <h2 class="section-title"><el-icon><DataAnalysis /></el-icon>数据选择</h2>
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
                <el-date-picker v-model="config.dateRange" type="daterange" range-separator="-" start-placeholder="开始" end-placeholder="结束" class="field-input" />
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
                <h2 class="section-title"><el-icon><Cpu /></el-icon>模型选择</h2>
                <p class="section-hint">选择算法并调整参数</p>
              </div>
            </div>
            <div class="model-grid">
              <div v-for="m in models" :key="m.id" class="model-item" :class="{ selected: config.model === m.id, disabled: !m.available }" @click="m.available && (config.model = m.id)">
                <div class="model-check" v-if="config.model === m.id">
                  <svg viewBox="0 0 16 16" fill="none"><path d="M3 8L7 12L13 4" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </div>
                <div class="model-name">{{ m.name }}</div>
                <div class="model-desc">{{ m.description }}</div>
              </div>
            </div>
            <div class="param-section">
              <div class="param-row">
                <span class="param-label">{{ forecastLabel }}</span>
                <div class="param-control">
                  <el-slider v-model="config.forecastDays" :min="forecastMin" :max="forecastMax" :step="forecastStep" class="slider" />
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
                <h2 class="section-title"><el-icon><VideoPlay /></el-icon>运行模型</h2>
                <p class="section-hint">启动预测流程</p>
              </div>
            </div>
            <div class="run-area">
              <button class="run-btn" :disabled="isRunning" @click="runModel">
                <span v-if="!isRunning" class="run-btn-text">
                  <svg viewBox="0 0 20 20" fill="currentColor" class="play-icon"><path d="M6 4L16 10L6 16V4Z"/></svg>
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
                    <svg v-if="step.status === 'done'" viewBox="0 0 16 16" fill="none"><path d="M3 8L7 12L13 4" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    <span v-else-if="step.status === 'active'" class="dot-pulse"></span>
                  </div>
                  <span class="tracker-text">{{ step.text }}</span>
                </div>
              </div>
              <div v-if="modelResult" class="result-cards">
                <div class="result-card">
                  <el-icon class="result-icon"><TrendCharts /></el-icon>
                  <span class="result-label">{{ totalLabel }}</span>
                  <span class="result-value">{{ modelResult.total_volume }}<small> 万m³</small></span>
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
                  <h2 class="section-title"><el-icon><PieChart /></el-icon>可视化输出</h2>
                  <p class="section-hint">预测结果趋势图</p>
                </div>
                <div class="chart-toggle">
                  <button v-for="t in chartTypes" :key="t.key" class="toggle-btn" :class="{active: chartType === t.key}" @click="chartType = t.key">{{ t.label }}</button>
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
    <aside class="ai-panel" :style="{ flexBasis: ((1 - splitRatio) * 100) + '%' }">
      <div class="ai-header">
        <div class="ai-title-wrap">
          <div class="ai-avatar">
            <svg viewBox="0 0 24 24" fill="none"><path d="M12 2L2 7V17L12 22L22 17V7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M12 2V22M2 7L22 17M22 7L2 17" stroke="currentColor" stroke-width="1" opacity="0.3"/></svg>
          </div>
          <div>
            <div class="ai-title">智能分析助手</div>
            <div class="ai-status"><span class="status-dot"></span> 在线</div>
          </div>
        </div>
        <button class="ai-clear" @click="clearChat">清空</button>
      </div>
      <div class="ai-messages" ref="messagesRef">
        <div v-for="msg in messages" :key="msg.id" class="msg" :class="msg.role">
          <div class="msg-avatar" :class="msg.role">
            <span v-if="msg.role === 'user'">我</span>
            <svg v-else viewBox="0 0 24 24" fill="none"><path d="M12 2L2 7V17L12 22L22 17V7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>
          </div>
          <div class="msg-body">
            <div class="msg-bubble" v-html="msg.content"></div>
          </div>
        </div>
        <div v-if="isStreaming" class="msg assistant">
          <div class="msg-avatar assistant">
            <svg viewBox="0 0 24 24" fill="none"><path d="M12 2L2 7V17L12 22L22 17V7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>
          </div>
          <div class="msg-body">
            <div class="msg-bubble typing"><span></span><span></span><span></span></div>
          </div>
        </div>
      </div>
      <div class="ai-input">
        <el-input v-model="inputMessage" type="textarea" :rows="2" placeholder="输入问题，获取智能分析..." @keydown.enter.ctrl="sendMessage" resize="none" />
        <button v-if="isStreaming" class="send-btn stop-btn" @click="stopStreaming">
          <svg viewBox="0 0 20 20" fill="currentColor"><rect x="5" y="5" width="10" height="10" rx="2"/></svg>
        </button>
        <button v-else class="send-btn" :disabled="!inputMessage.trim()" @click="sendMessage">
          <svg viewBox="0 0 20 20" fill="currentColor"><path d="M2 10L18 2L10 18L8 11L2 10Z"/></svg>
        </button>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { marked } from 'marked'
import { Cpu, DataAnalysis, DataLine, Finished, Grid, Histogram, Location, MagicStick, PieChart, TrendCharts, User, VideoPlay } from '@element-plus/icons-vue'
import type { ChatMessage } from '@/types'

const renderMarkdown = (source: string): string => marked(source, { async: false })

const route = useRoute()
const chartRef = ref<HTMLElement>()
const chartRefTop = ref<HTMLElement>()
const chartRefBottom = ref<HTMLElement>()
const messagesRef = ref<HTMLElement>()
const agentId = computed(() => String(route.meta.agentId || 'winter-supply'))
const isMonthlySales = computed(() => agentId.value === 'monthly-sales')
const isShortTerm = computed(() => agentId.value === 'short-term')
const isWinterSupply = computed(() => agentId.value === 'winter-supply')

const chartTypes = [
  { key: 'line', label: '折线' },
  { key: 'bar', label: '柱状' },
  { key: 'area', label: '面积' }
]

const isRunning = ref(false)
const chartType = ref('line')
const streamingSet = reactive(new Set<string>())
const isStreaming = computed(() => streamingSet.has(agentId.value))
const abortControllers = new Map<string, AbortController>()
const inputMessage = ref('')

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
      [chartRef, chartRefTop, chartRefBottom].forEach(r => {
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
  '城市燃气': '城市燃气', '工业燃料': '工业燃料', '天然气化工': '化工', '天然气发电': '发电', 'CNG': 'CNG', 'LNG': 'LNG'
}
const industryColors: Record<string, string> = {
  '城市燃气': 'linear-gradient(135deg, #0EA5E9, #0284C7)',
  '工业燃料': 'linear-gradient(135deg, #10B981, #059669)',
  '天然气化工': 'linear-gradient(135deg, #F59E0B, #D97706)',
  '天然气发电': 'linear-gradient(135deg, #8B5CF6, #6D28D9)',
  'CNG': 'linear-gradient(135deg, #EC4899, #DB2777)',
  'LNG': 'linear-gradient(135deg, #06B6D4, #0891B2)'
}
const selectedProvince = ref('江苏')
const selectedIndustry = ref('城市燃气')
const currentResult = ref<any>(null)
const loadingResult = ref(false)
const provinceMape = ref<Record<string, number>>({})

const mapeClass = (mape: number) => {
  if (mape <= 10) return 'metric-good'
  if (mape <= 30) return 'metric-ok'
  return 'metric-bad'
}

// 通用智能体的计算属性
const forecastLabel = computed(() => agentId.value === 'monthly-sales' ? '预测月数' : '预测天数')
const forecastUnit = computed(() => agentId.value === 'monthly-sales' ? '月' : '天')
const forecastMin = computed(() => agentId.value === 'monthly-sales' ? 3 : 7)
const forecastMax = computed(() => agentId.value === 'monthly-sales' ? 12 : 90)
const forecastStep = computed(() => agentId.value === 'monthly-sales' ? 1 : 7)
const totalLabel = computed(() => agentId.value === 'monthly-sales' ? '预测总销量' : agentId.value === 'short-term' ? '预测总用气量' : '预测总需求量')

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

// === 月度预测：加载结果 ===
const loadProvinceMape = async () => {
  try {
    const resp = await fetch('/monthly-results')
    const data = await resp.json()
    const mapeMap: Record<string, number> = {}
    for (const r of data.results) {
      if (r.province === selectedProvince.value) {
        const indKey = r.industry === '化工' ? '天然气化工' : r.industry === '发电' ? '天然气发电' : r.industry
        mapeMap[indKey] = r.metrics.mape
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
    const resp = await fetch(`/monthly-results/detail?${queryString({ province: selectedProvince.value, industry: industryKey })}`)
    const data = await resp.json()
    if (!data.error) {
      const monthlyData = normalizeMonthlyResult(data)
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
const selectedStProvince = ref('江苏')
const stIndustries = ref<string[]>([])
const stIndustryColors: Record<string, string> = {
  '城市燃气': 'linear-gradient(135deg, #0EA5E9, #0284C7)',
  'CNG': 'linear-gradient(135deg, #EC4899, #DB2777)',
  '化工': 'linear-gradient(135deg, #F59E0B, #D97706)',
  '工业燃料': 'linear-gradient(135deg, #10B981, #059669)',
}
const selectedStIndustry = ref('城市燃气')
const selectedStCustomer = ref('')
const stCustomers = ref<string[]>([])
const stProvinceMape = ref<Record<string, number>>({})
const stAllResults = ref<any[]>([])

const queryString = (params: Record<string, string>) => new URLSearchParams(params).toString()

const syncShortTermOptions = () => {
  const scopedResults = stAllResults.value.filter(r => r.province === selectedStProvince.value)
  const industries = Array.from(new Set(scopedResults.map(r => r.industry).filter(Boolean)))
  stIndustries.value = industries.length > 0 ? industries : ['城市燃气', 'CNG', '化工', '工业燃料']

  if (!stIndustries.value.includes(selectedStIndustry.value)) {
    selectedStIndustry.value = stIndustries.value[0] || ''
  }

  const selectedIndustryResults = scopedResults.filter(r => r.industry === selectedStIndustry.value)
  const customerSet = new Set<string>()
  for (const result of selectedIndustryResults) {
    if (result.customer) {
      customerSet.add(result.customer)
    }
    if (Array.isArray(result.customers)) {
      result.customers.forEach((customer: string) => customerSet.add(customer))
    }
  }
  stCustomers.value = Array.from(customerSet)
  if (selectedStCustomer.value && !stCustomers.value.includes(selectedStCustomer.value)) {
    selectedStCustomer.value = ''
  }

  const mapeMap: Record<string, number> = {}
  for (const result of scopedResults) {
    if (!result.customer && result.metrics?.mape !== undefined) {
      mapeMap[result.industry] = result.metrics.mape
    }
  }
  stProvinceMape.value = mapeMap
}

const loadStMape = async () => {
  try {
    const resp = await fetch('/short-term-results')
    const data = await resp.json()
    stAllResults.value = data.results || []
    const provSet = new Set<string>()
    for (const r of stAllResults.value) {
      provSet.add(r.province)
    }
    stProvinces.value = Array.from(provSet)
    if (stProvinces.value.length > 0 && !stProvinces.value.includes(selectedStProvince.value)) {
      selectedStProvince.value = stProvinces.value[0]
    }
    syncShortTermOptions()
  } catch (e) { console.error('加载短期MAPE失败:', e) }
}

const loadStResult = async () => {
  loadingResult.value = true
  currentResult.value = null
  try {
    let url = `/short-term-results/detail?${queryString({ province: selectedStProvince.value, industry: selectedStIndustry.value })}`
    if (selectedStCustomer.value) {
      url = `/short-term-results/customer-detail?${queryString({ province: selectedStProvince.value, industry: selectedStIndustry.value, customer: selectedStCustomer.value })}`
    }
    const resp = await fetch(url)
    const data = await resp.json()
    if (!data.error) {
      currentResult.value = data
      nextTick(() => initMonthlyChart(data))
    }
  } catch (e) { console.error('加载短期预测结果失败:', e) }
  finally { loadingResult.value = false }
}

const switchStProvince = (province: string) => {
  selectedStProvince.value = province
  selectedStCustomer.value = ''
  syncShortTermOptions()
  loadStResult()
}

const switchStIndustry = (industry: string) => {
  selectedStIndustry.value = industry
  selectedStCustomer.value = ''
  syncShortTermOptions()
  loadStResult()
}

const switchStCustomer = (customer: string) => {
  selectedStCustomer.value = customer
  loadStResult()
}

// === 冬供保供预测专用状态 ===
const wsProvinces = ['江苏', '河北']
const selectedWsProvince = ref('江苏')
const wsProvinceMape = ref<Record<string, number>>({})

const loadWsMape = async () => {
  try {
    const resp = await fetch('/winter-supply-results')
    const data = await resp.json()
    const mapeMap: Record<string, number> = {}
    for (const r of data.results) {
      mapeMap[r.province] = r.metrics.mape
    }
    wsProvinceMape.value = mapeMap
  } catch (e) { console.error('加载冬供MAPE失败:', e) }
}

const loadWsResult = async () => {
  loadingResult.value = true
  currentResult.value = null
  try {
    const resp = await fetch(`/winter-supply-results/detail?${queryString({ province: selectedWsProvince.value })}`)
    const data = await resp.json()
    if (!data.error) {
      currentResult.value = data
      nextTick(() => initMonthlyChart(data))
    }
  } catch (e) { console.error('加载冬供预测结果失败:', e) }
  finally { loadingResult.value = false }
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
    await loadStResult()
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
    const chart = echarts.init(chartRefTop.value)
    const series: any[] = [
      {
        name: '实际值', type: 'line', smooth: true,
        data: data.actual || [],
        itemStyle: { color: '#10b981' }, lineStyle: { width: 1.5 }, symbol: 'circle', symbolSize: 3
      },
      {
        name: '预测值', type: chartType.value === 'bar' ? 'bar' : 'line', smooth: true,
        data: data.predicted || [],
        itemStyle: { color: '#3b82f6' }, lineStyle: { width: 1.5 }, symbol: 'square', symbolSize: 3,
        areaStyle: chartType.value === 'area' ? { color: 'rgba(59,130,246,0.1)' } : undefined
      }
    ]
    chart.setOption({
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#E2E8F0', textStyle: { color: '#0F172A', fontSize: 12 } },
      legend: { bottom: 0, textStyle: { color: '#64748B', fontSize: 11 }, data: ['实际值', '预测值'] },
      grid: { left: '2%', right: '4%', top: '3%', bottom: 50, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: data.dates || [], axisLine: { lineStyle: { color: '#E2E8F0' } }, axisLabel: { color: '#94A3B8', fontSize: 10, rotate: 30 } },
      yAxis: { type: 'value', name: '万m³', splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8', fontSize: 11 } },
      dataZoom: [
          { type: 'inside', start: 0, end: 100, zoomOnMouseWheel: true },
          { type: 'slider', start: 0, end: 100, height: 10, bottom: 28, borderColor: '#E2E8F0', fillerColor: 'rgba(59,130,246,0.1)', handleStyle: { color: '#3b82f6' } }
        ],
      series
    })
    ;(chartRefTop.value as any)._echarts = chart
  }

  // === 下图：历史与未来预测 ===
  if (chartRefBottom.value && data.future_dates) {
    const chartB = echarts.init(chartRefBottom.value)
    // 使用完整历史数据 + 未来预测
    const histDates = data.history_dates || data.dates
    const histValues = data.history_values || data.actual
    const allDates = [...histDates, ...(data.future_dates || [])]
    const histPadding = (data.future_dates || []).map(() => null)
    const futurePadding = histDates.map(() => null)
    const futurePred = [...futurePadding, ...(data.future_predicted || [])]

    const seriesB: any[] = [
      {
        name: '历史数据', type: 'line', smooth: true,
        data: [...histValues, ...histPadding],
        itemStyle: { color: '#10b981' }, lineStyle: { width: 1.2 }, symbol: 'none', connectNulls: false
      },
      {
        name: '未来预测', type: 'line', smooth: true,
        data: futurePred,
        itemStyle: { color: '#f59e0b' }, lineStyle: { width: 2 }, symbol: 'circle', symbolSize: 3, connectNulls: false
      }
    ]
    // 预测起点标记
    const markLine = {
      symbol: 'none',
      silent: true,
      data: [{ xAxis: histDates.length, lineStyle: { color: '#f59e0b', type: 'dashed', width: 1.5 }, label: { show: true, formatter: '预测起点', color: '#f59e0b', fontSize: 10, position: 'start' } }]
    }
    seriesB[1].markLine = markLine

    chartB.setOption({
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#E2E8F0', textStyle: { color: '#0F172A', fontSize: 12 } },
      legend: { bottom: 0, textStyle: { color: '#64748B', fontSize: 11 }, data: ['历史数据', '未来预测'] },
      grid: { left: '2%', right: '4%', top: '3%', bottom: 50, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: allDates, axisLine: { lineStyle: { color: '#E2E8F0' } }, axisLabel: { color: '#94A3B8', fontSize: 10, rotate: 30 } },
      yAxis: { type: 'value', name: '万m³', splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8', fontSize: 11 } },
      dataZoom: [
            { type: 'inside', start: 0, end: 100, zoomOnMouseWheel: true },
            { type: 'slider', start: 0, end: 100, height: 10, bottom: 28, borderColor: '#E2E8F0', fillerColor: 'rgba(245,158,11,0.1)', handleStyle: { color: '#f59e0b' } }
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
  } catch (e) { console.error(e) }
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
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agent_id: agentId.value, data_source: config.dataSource, date_range: config.dateRange, region: config.region, model: config.model, forecast_days: config.forecastDays })
    })
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    const nodeMap: Record<string, number> = { 'load_data': 0, 'preprocess': 1, 'train_model': 2, 'predict': 3, 'analyze': 4 }

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
              progressSteps.value.forEach(s => s.status = 'done')
              modelResult.value = event.data
              nextTick(() => initChart(event.data))
              sendAnalysisMessage(event.content || '')
            }
          } catch (e) {}
        }
      }
    }
  } catch (e) { console.error(e) }
  finally { isRunning.value = false }
}

const initChart = (data: any) => {
  if (!chartRef.value) return
  const chart = echarts.init(chartRef.value)
  const isShort = agentId.value === 'short-term' && data.type_chart_data

  if (isShort) {
    const colors = ['#0EA5E9', '#10B981', '#F59E0B']
    const names = ['工业用户', '商业用户', '居民用户']
    const series = Object.entries(data.type_chart_data).map(([type, values]: [string, any], i) => ({
      name: type === 'industrial' ? names[0] : type === 'commercial' ? names[1] : names[2],
      type: chartType.value === 'bar' ? 'bar' : 'line', smooth: true, data: values,
      itemStyle: { color: colors[i % 3] },
      areaStyle: chartType.value === 'area' ? { opacity: 0.15 } : undefined,
      lineStyle: { width: 2.5 }
    }))
    chart.setOption({
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#E2E8F0', textStyle: { color: '#0F172A', fontSize: 12 } },
      legend: { data: series.map(s => s.name), bottom: 0, textStyle: { color: '#64748B', fontSize: 12 } },
      grid: { left: '2%', right: '4%', top: '4%', bottom: '12%', containLabel: true },
      xAxis: { type: 'category', data: data.dates, boundaryGap: false, axisLine: { lineStyle: { color: '#E2E8F0' } }, axisLabel: { color: '#94A3B8', fontSize: 11 } },
      yAxis: { type: 'value', name: '万m³', splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8', fontSize: 11 } },
      series
    })
  } else {
    const series: any[] = [{
      name: '预测值', type: chartType.value === 'bar' ? 'bar' : 'line', smooth: true, data: data.predicted,
      itemStyle: { color: '#0EA5E9' }, lineStyle: { width: 3 }, symbol: 'circle', symbolSize: 5,
      areaStyle: chartType.value === 'area' ? { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(14,165,233,0.2)' }, { offset: 1, color: 'rgba(14,165,233,0)' }]) } : undefined
    }]
    if (data.supply_forecast) {
      series.push({ name: '供应能力', type: 'line', smooth: true, data: data.supply_forecast, itemStyle: { color: '#10B981' }, lineStyle: { width: 2, type: 'dashed' } })
    }
    chart.setOption({
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#E2E8F0', textStyle: { color: '#0F172A', fontSize: 12 } },
      legend: { bottom: 0, textStyle: { color: '#64748B', fontSize: 12 } },
      grid: { left: '2%', right: '4%', top: '4%', bottom: '12%', containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: data.dates, axisLine: { lineStyle: { color: '#E2E8F0' } }, axisLabel: { color: '#94A3B8', fontSize: 11 } },
      yAxis: { type: 'value', name: '万m³', splitLine: { lineStyle: { color: '#F1F5F9' } }, axisLabel: { color: '#94A3B8', fontSize: 11 } },
      series
    })
  }
  ;(chartRef.value as any)._echarts = chart
}

// === AI 对话 ===
const stopStreaming = () => {
  const controller = abortControllers.get(agentId.value)
  if (controller) controller.abort()
}

const updateMsgInStorage = (id: string, msgId: string, content: string) => {
  try {
    const saved = sessionStorage.getItem(getChatKey(id))
    if (saved) {
      const msgs: ChatMessage[] = JSON.parse(saved)
      const msg = msgs.find(m => m.id === msgId)
      if (msg) { msg.content = content; sessionStorage.setItem(getChatKey(id), JSON.stringify(msgs)) }
    }
  } catch {}
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || isStreaming.value) return
  const userMessage = inputMessage.value
  inputMessage.value = ''
  const streamId = agentId.value
  messages.value.push({ id: Date.now().toString(), role: 'user', content: userMessage, time: new Date().toLocaleTimeString() })
  scrollToBottom()
  streamingSet.add(streamId)
  const msgId = (Date.now() + 1).toString()
  messages.value.push({ id: msgId, role: 'assistant', content: '', time: new Date().toLocaleTimeString() })
  saveChatToStorage(streamId)

  const controller = new AbortController()
  abortControllers.set(streamId, controller)
  let fullResponse = ''

  try {
    const context = (isMonthlySales.value || isShortTerm.value || isWinterSupply.value) ? currentResult.value : modelResult.value
    const response = await fetch('/chat', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agent_id: streamId, message: userMessage, context: context || {}, conversation_history: messages.value.slice(-6).map(m => ({ role: m.role === 'user' ? 'user' : 'assistant', content: m.content })) }),
      signal: controller.signal
    })
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
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
            if (event.type === 'stream') {
              fullResponse += event.content
              if (agentId.value === streamId) {
                const msg = messages.value.find(m => m.id === msgId)
                if (msg) msg.content = renderMarkdown(fullResponse)
                scrollToBottom()
              }
            } else if (event.type === 'complete') {
              fullResponse = event.content
              if (agentId.value === streamId) {
                const msg = messages.value.find(m => m.id === msgId)
                if (msg) msg.content = renderMarkdown(event.content)
              }
            } else if (event.type === 'error') {
              fullResponse = '⚠️ ' + event.message
              if (agentId.value === streamId) {
                const msg = messages.value.find(m => m.id === msgId)
                if (msg) msg.content = renderMarkdown(fullResponse)
              }
            }
          } catch (e) {}
        }
      }
    }
    // Stream completed
    if (agentId.value === streamId) {
      saveChatToStorage(streamId)
    } else {
      updateMsgInStorage(streamId, msgId, renderMarkdown(fullResponse))
    }
  } catch (e: any) {
    if (e.name === 'AbortError') {
      const content = renderMarkdown(fullResponse || '（已停止）')
      if (agentId.value === streamId) {
        const msg = messages.value.find(m => m.id === msgId)
        if (msg) msg.content = content
        saveChatToStorage(streamId)
      } else {
        updateMsgInStorage(streamId, msgId, content)
      }
    } else {
      if (agentId.value === streamId) {
        const msg = messages.value.find(m => m.id === msgId)
        if (msg) msg.content = renderMarkdown('⚠️ 网络错误')
      }
    }
  } finally {
    streamingSet.delete(streamId)
    abortControllers.delete(streamId)
    if (agentId.value === streamId) scrollToBottom()
  }
}

const sendAnalysisMessage = async (analysisText: string) => {
  streamingSet.add(agentId.value)
  const msgId = Date.now().toString()
  messages.value.push({ id: msgId, role: 'assistant', content: '', time: new Date().toLocaleTimeString() })
  if (analysisText && analysisText.length > 20) {
    const msg = messages.value.find(m => m.id === msgId)
    if (msg) {
      const html = renderMarkdown(analysisText)
      for (let i = 0; i <= html.length; i += Math.ceil(html.length / 40)) {
        await new Promise(r => setTimeout(r, 12))
        msg.content = html.substring(0, i)
        scrollToBottom()
      }
      msg.content = html
    }
    streamingSet.delete(agentId.value)
    saveChatToStorage(agentId.value)
    scrollToBottom()
  }
}

const clearChat = () => {
  messages.value = [{ id: Date.now().toString(), role: 'assistant', content: renderMarkdown('对话已清空，请随时提问！'), time: new Date().toLocaleTimeString() }]
  saveChatToStorage(agentId.value)
}

const getChatKey = (id: string) => `chat_${id}`

const saveChatToStorage = (id: string) => {
  try { sessionStorage.setItem(getChatKey(id), JSON.stringify(messages.value)) } catch {}
}

const loadChatFromStorage = (id: string): boolean => {
  try {
    const saved = sessionStorage.getItem(getChatKey(id))
    if (saved) {
      messages.value = JSON.parse(saved)
      return true
    }
  } catch {}
  return false
}

const getGreeting = (id: string): string => {
  if (id === 'monthly-sales') return '您好！我是月度销量预测分析助手。\n\n请在左侧选择省份和行业，查看12个预测模型的结果。\n\n您可以随时向我提问关于预测趋势、模型精度、行业对比等方面的问题。'
  if (id === 'short-term') return '您好！我是短期客户预测分析助手。\n\n请在左侧选择行业和客户，查看短期用气量预测结果。\n\n城市燃气行业支持按客户查看预测，其他行业展示整体预测数据。'
  if (id === 'winter-supply') return '您好！我是冬季保供预测分析助手。\n\n请在左侧选择省份，查看冬供旬度预测结果。\n\n您可以随时向我提问关于冬供趋势、模型精度、供需分析等方面的问题。'
  return '您好！我是智能分析助手。\n\n请在左侧完成数据选择和模型配置，运行预测后我将为您详细分析结果。\n\n您可以随时向我提问关于预测趋势、异常分析、业务建议等方面的问题。'
}

const initOrRestoreChat = (id: string) => {
  inputMessage.value = ''
  if (!loadChatFromStorage(id)) {
    messages.value = [{ id: '1', role: 'assistant', content: renderMarkdown(getGreeting(id)), time: new Date().toLocaleTimeString() }]
  }
}

const scrollToBottom = () => { nextTick(() => { if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight }) }

const messages = ref<ChatMessage[]>([{ id: '1', role: 'assistant', content: renderMarkdown('您好！我是智能分析助手。\n\n请在左侧完成数据选择和模型配置，运行预测后我将为您详细分析结果。\n\n您可以随时向我提问关于预测趋势、异常分析、业务建议等方面的问题。'), time: new Date().toLocaleTimeString() }])

let resizeHandler: (() => void) | null = null
onMounted(() => {
  initOrRestoreChat(agentId.value)
  if (isMonthlySales.value) {
    loadProvinceMape()
    loadMonthlyResult()
  } else if (isShortTerm.value) {
    loadStMape()
    loadStResult()
  } else if (isWinterSupply.value) {
    loadWsMape()
    loadWsResult()
  } else {
    loadAgentConfig()
  }
  resizeHandler = () => {
    [chartRef, chartRefTop, chartRefBottom].forEach(ref => {
      if (ref.value && (ref.value as any)._echarts) (ref.value as any)._echarts.resize()
    })
  }
  window.addEventListener('resize', resizeHandler)
})

// 切换智能体时重新加载
watch(agentId, (newId, oldId) => {
  if (newId === oldId) return
  saveChatToStorage(oldId)
  modelResult.value = null
  currentResult.value = null
  initOrRestoreChat(newId)
  if (isMonthlySales.value) {
    loadProvinceMape()
    loadMonthlyResult()
  } else if (isShortTerm.value) {
    selectedStProvince.value = '江苏'
    selectedStIndustry.value = '城市燃气'
    selectedStCustomer.value = ''
    loadStMape()
    loadStResult()
  } else if (isWinterSupply.value) {
    selectedWsProvince.value = '江苏'
    loadWsMape()
    loadWsResult()
  } else {
    loadAgentConfig()
  }
})

// 图表类型切换时重绘
watch(chartType, () => {
  if (currentResult.value && (isMonthlySales.value || isShortTerm.value || isWinterSupply.value)) {
    nextTick(() => initMonthlyChart(currentResult.value))
  } else if (modelResult.value) {
    nextTick(() => initChart(modelResult.value))
  }
})
</script>

<style scoped>
.agent-detail {
  display: flex;
  height: 100%;
  min-height: 680px;
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
  background: white;
  border: 1px solid #F1F5F9;
  border-radius: 16px;
  overflow: hidden;
}
.workspace-scroll {
  height: 100%;
  overflow-y: auto;
  padding: 24px 28px 32px;
}

/* 区块 */
.section-block {
  padding-bottom: 28px;
  margin-bottom: 28px;
  border-bottom: 1px solid #F1F5F9;
}
.section-block:last-child { border-bottom: none; margin-bottom: 0; }

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
  width: 32px; height: 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0EA5E9, #0284C7);
  color: white;
  font-size: 13px;
  font-weight: 700;
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
  font-weight: 800;
  color: #0F172A;
  letter-spacing: 0;
  margin-bottom: 3px;
}
.section-title .el-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: #E0F2FE;
  color: #0284C7;
  font-size: 17px;
  flex: 0 0 auto;
}
.section-block:nth-of-type(4n + 1) .section-title .el-icon {
  background: #E0F2FE;
  color: #0284C7;
}
.section-block:nth-of-type(4n + 2) .section-title .el-icon {
  background: #DCFCE7;
  color: #16A34A;
}
.section-block:nth-of-type(4n + 3) .section-title .el-icon {
  background: #FEF3C7;
  color: #D97706;
}
.section-block:nth-of-type(4n) .section-title .el-icon {
  background: #F3E8FF;
  color: #7C3AED;
}
.section-hint { font-size: 12px; color: #94A3B8; font-weight: 500; }
.refresh-result-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  flex: 0 0 auto;
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid #BAE6FD;
  border-radius: 8px;
  background: #F0F9FF;
  color: #0284C7;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s;
}
.refresh-result-btn:hover:not(:disabled) {
  border-color: #0EA5E9;
  background: #E0F2FE;
  box-shadow: 0 4px 12px -6px rgba(14,165,233,0.45);
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
  border: 2px solid rgba(2,132,199,0.22);
  border-top-color: #0284C7;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* 表单 */
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.form-field { display: flex; flex-direction: column; gap: 6px; }
.form-field.full { grid-column: 1 / -1; }
.form-field label { font-size: 12px; font-weight: 600; color: #475569; }
.field-input { width: 100%; }

/* 省份选择器 */
.region-selector { display: flex; gap: 8px; }
.region-btn {
  padding: 10px 24px;
  border: 1.5px solid #E2E8F0;
  background: white;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #64748B;
  cursor: pointer;
  transition: all 0.2s;
}
.region-btn:hover { border-color: #7DD3FC; background: #F0F9FF; }
.region-btn.active {
  border-color: #0EA5E9;
  background: linear-gradient(135deg, #0EA5E9, #0284C7);
  color: white;
  box-shadow: 0 4px 12px -2px rgba(14,165,233,0.3);
}
.region-mape {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  background: rgba(255,255,255,0.2);
}
.region-btn:not(.active) .region-mape {
  background: #F1F5F9;
  color: #64748B;
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
  width: 40px; height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
}
.industry-select-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.industry-select-name { font-size: 14px; font-weight: 600; color: #0F172A; }
.industry-select-mape { font-size: 12px; color: #94A3B8; font-weight: 500; }
.industry-select { flex: 1; }
.industry-select :deep(.el-select__wrapper) {
  border-radius: 10px;
  border: 1.5px solid #E2E8F0;
  box-shadow: none;
  padding: 10px 14px;
  background: #FAFBFC;
  transition: border-color 0.2s;
}
.industry-select :deep(.el-select__wrapper:hover) {
  border-color: #7DD3FC;
}
.industry-select :deep(.el-select__wrapper.is-focused) {
  border-color: #0EA5E9;
  box-shadow: 0 0 0 2px rgba(14,165,233,0.1);
}
.industry-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}
.industry-option-icon {
  width: 28px; height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.industry-option-name { font-size: 14px; font-weight: 500; color: #0F172A; flex: 1; }
.industry-option-mape { font-size: 12px; color: #94A3B8; font-weight: 500; }

/* 模型信息卡片 */
.model-info-card {
  background: #F8FAFC;
  border: 1px solid #F1F5F9;
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
  font-weight: 800;
  color: #0F172A;
  letter-spacing: 0;
}
.model-info-name .el-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, #E0F2FE, #DCFCE7);
  color: #0284C7;
  font-size: 17px;
  flex: 0 0 auto;
}
.model-info-desc { font-size: 13px; color: #64748B; margin-top: 6px; font-weight: 500; }
.model-info-tag {
  padding: 4px 12px;
  background: linear-gradient(135deg, #0EA5E9, #0284C7);
  color: white;
  border-radius: 6px;
  font-size: 11px;
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
  border: 1px solid #F1F5F9;
}
.metric-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: #F0F9FF;
  color: #0284C7;
  font-size: 17px;
}
.metric-item:nth-child(1) .metric-icon {
  background: #DCFCE7;
  color: #16A34A;
}
.metric-item:nth-child(2) .metric-icon {
  background: #E0F2FE;
  color: #0284C7;
}
.metric-item:nth-child(3) .metric-icon {
  background: #FEF3C7;
  color: #D97706;
}
.metric-item:nth-child(4) .metric-icon {
  background: #F3E8FF;
  color: #7C3AED;
}
.metric-label { font-size: 12px; color: #64748B; font-weight: 700; text-transform: uppercase; letter-spacing: 0; }
.metric-value { font-size: 24px; font-weight: 800; color: #0F172A; line-height: 1.1; }
.metric-value.metric-good { color: #10B981; }
.metric-value.metric-ok { color: #F59E0B; }
.metric-value.metric-bad { color: #EF4444; }

/* 数据摘要 */
.data-summary {
  display: flex;
  gap: 16px;
  margin-top: 16px;
  padding: 16px;
  background: #F8FAFC;
  border-radius: 10px;
}
.summary-item { display: flex; flex-direction: column; gap: 2px; }
.summary-label { font-size: 11px; color: #94A3B8; font-weight: 600; }
.summary-value { font-size: 14px; font-weight: 600; color: #0F172A; }

/* 加载中 */
.loading-area { display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 40px; }
.loading-spinner { width: 32px; height: 32px; border: 3px solid #F1F5F9; border-top-color: #0EA5E9; border-radius: 50%; animation: spin 0.8s linear infinite; }
.loading-text { font-size: 14px; color: #94A3B8; }

/* 模型选择(通用) */
.model-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 20px; }
.model-item { position: relative; padding: 16px; border: 1.5px solid #F1F5F9; border-radius: 12px; cursor: pointer; transition: all 0.2s; }
.model-item:hover:not(.disabled) { border-color: #7DD3FC; background: #F0F9FF; }
.model-item.selected { border-color: #0EA5E9; background: #F0F9FF; }
.model-item.disabled { opacity: 0.4; cursor: not-allowed; }
.model-check { position: absolute; top: 12px; right: 12px; width: 18px; height: 18px; background: #0EA5E9; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.model-check svg { width: 12px; height: 12px; }
.model-name { font-size: 14px; font-weight: 600; color: #0F172A; margin-bottom: 2px; }
.model-desc { font-size: 12px; color: #94A3B8; }

.param-section { display: flex; flex-direction: column; gap: 20px; padding: 20px; background: #F8FAFC; border-radius: 12px; }
.param-row { display: flex; align-items: center; gap: 16px; }
.param-label { font-size: 13px; font-weight: 600; color: #475569; min-width: 70px; }
.param-control { flex: 1; display: flex; align-items: center; gap: 12px; }
.slider { flex: 1; }
.param-val { font-size: 13px; font-weight: 700; color: #0EA5E9; min-width: 50px; text-align: right; }

/* 运行 */
.run-area { display: flex; flex-direction: column; align-items: center; gap: 24px; }
.run-btn { padding: 14px 40px; border: none; border-radius: 12px; font-size: 15px; font-weight: 600; cursor: pointer; background: linear-gradient(135deg, #0EA5E9, #0284C7); color: white; transition: all 0.2s; display: flex; align-items: center; gap: 8px; }
.run-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 8px 20px -6px rgba(14,165,233,0.4); }
.run-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.run-btn-text { display: flex; align-items: center; gap: 8px; }
.play-icon { width: 18px; height: 18px; }
.spinner { width: 16px; height: 16px; border: 2px solid rgba(255,255,255,0.3); border-top-color: white; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.progress-tracker { display: flex; flex-direction: column; gap: 8px; width: 100%; max-width: 400px; }
.tracker-item { display: flex; align-items: center; gap: 12px; padding: 8px 12px; border-radius: 8px; transition: all 0.3s; }
.tracker-item.done { color: #10B981; }
.tracker-item.active { background: #F0F9FF; color: #0EA5E9; }
.tracker-item.pending { color: #CBD5E1; }
.tracker-dot { width: 20px; height: 20px; border-radius: 50%; border: 2px solid currentColor; display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: all 0.3s; }
.tracker-item.done .tracker-dot { background: #10B981; border-color: #10B981; }
.tracker-dot svg { width: 12px; height: 12px; color: white; }
.dot-pulse { width: 8px; height: 8px; border-radius: 50%; background: currentColor; animation: pulse 1.4s ease-in-out infinite; }
@keyframes pulse { 0%,100% { transform: scale(0.8); opacity: 0.5; } 50% { transform: scale(1.2); opacity: 1; } }
.tracker-text { font-size: 13px; font-weight: 500; }

.result-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; width: 100%; max-width: 500px; }
.result-card { padding: 16px; background: #F0F9FF; border: 1px solid #BAE6FD; border-radius: 12px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 6px; }
.result-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: white;
  color: #0284C7;
  font-size: 17px;
  box-shadow: 0 1px 2px rgba(14,165,233,0.12);
}
.result-card:nth-child(1) .result-icon {
  background: #DCFCE7;
  color: #16A34A;
}
.result-card:nth-child(2) .result-icon {
  background: #E0F2FE;
  color: #0284C7;
}
.result-card:nth-child(3) .result-icon {
  background: #F3E8FF;
  color: #7C3AED;
}
.result-label { font-size: 12px; color: #0284C7; font-weight: 700; text-transform: uppercase; letter-spacing: 0; }
.result-value { font-size: 25px; font-weight: 800; color: #0F172A; letter-spacing: 0; line-height: 1.1; }
.result-value small { font-size: 12px; font-weight: 500; color: #94A3B8; }

/* 图表 */
.chart-toggle { display: flex; gap: 2px; background: #F1F5F9; padding: 3px; border-radius: 8px; }
.toggle-btn { padding: 6px 14px; border: none; background: transparent; border-radius: 6px; font-size: 12px; font-weight: 600; color: #64748B; cursor: pointer; transition: all 0.2s; }
.toggle-btn.active { background: white; color: #0EA5E9; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
.chart-box { background: #FAFBFC; border-radius: 12px; padding: 16px; }
.chart-label { font-size: 13px; font-weight: 600; color: #475569; margin-bottom: 8px; margin-top: 12px; }
.chart-label:first-child { margin-top: 0; }
.chart { width: 100%; height: 100%; }

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
  background: #0EA5E9;
}

/* 右侧AI */
.ai-panel { flex: 1 1 0; min-width: 0; background: white; border: 1px solid #F1F5F9; border-radius: 16px; display: flex; flex-direction: column; overflow: hidden; }
.ai-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #F1F5F9; }
.ai-title-wrap { display: flex; align-items: center; gap: 10px; }
.ai-avatar { width: 36px; height: 36px; border-radius: 10px; background: linear-gradient(135deg, #0EA5E9, #06B6D4); display: flex; align-items: center; justify-content: center; color: white; }
.ai-avatar svg { width: 20px; height: 20px; }
.ai-title { font-size: 14px; font-weight: 600; color: #0F172A; }
.ai-status { font-size: 11px; color: #10B981; display: flex; align-items: center; gap: 4px; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; background: #10B981; }
.ai-clear { padding: 5px 12px; border: 1px solid #E2E8F0; background: white; border-radius: 6px; font-size: 12px; color: #64748B; cursor: pointer; transition: all 0.2s; }
.ai-clear:hover { background: #F8FAFC; border-color: #CBD5E1; }

.ai-messages { flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.msg { display: flex; gap: 10px; }
.msg.user { flex-direction: row-reverse; }
.msg-avatar { width: 28px; height: 28px; border-radius: 8px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 11px; font-weight: 700; }
.msg-avatar.assistant { background: linear-gradient(135deg, #0EA5E9, #06B6D4); color: white; }
.msg-avatar.user { background: #F1F5F9; color: #475569; }
.msg-avatar svg { width: 16px; height: 16px; }
.msg-body { max-width: 80%; }
.msg-bubble { padding: 10px 14px; border-radius: 12px; font-size: 13px; line-height: 1.6; }
.msg.assistant .msg-bubble { background: #F8FAFC; color: #1E293B; border-bottom-left-radius: 4px; }
.msg.user .msg-bubble { background: linear-gradient(135deg, #0EA5E9, #0284C7); color: white; border-bottom-right-radius: 4px; }
.msg-bubble :deep(h2) { font-size: 15px; margin: 0 0 8px; font-weight: 700; }
.msg-bubble :deep(h3) { font-size: 13px; margin: 10px 0 6px; font-weight: 600; }
.msg-bubble :deep(p) { margin: 4px 0; }
.msg-bubble :deep(ul), .msg-bubble :deep(ol) { margin: 6px 0; padding-left: 18px; }
.msg-bubble :deep(li) { margin: 3px 0; }
.msg-bubble :deep(strong) { font-weight: 600; }
.msg-bubble :deep(code) { background: rgba(0,0,0,0.06); padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.msg.user .msg-bubble :deep(code) { background: rgba(255,255,255,0.2); }

.msg-bubble.typing { display: flex; gap: 4px; padding: 14px 16px; }
.msg-bubble.typing span { width: 6px; height: 6px; border-radius: 50%; background: #94A3B8; animation: bounce 1.4s infinite ease-in-out; }
.msg-bubble.typing span:nth-child(2) { animation-delay: 0.2s; }
.msg-bubble.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%,60%,100% { transform: translateY(0); opacity: 0.4; } 30% { transform: translateY(-6px); opacity: 1; } }

.ai-input { padding: 12px 16px 16px; border-top: 1px solid #F1F5F9; display: flex; gap: 8px; align-items: flex-end; }
.ai-input :deep(.el-textarea__inner) { border-radius: 10px; border-color: #E2E8F0; font-size: 13px; padding: 8px 12px; resize: none; }
.ai-input :deep(.el-textarea__inner:focus) { border-color: #0EA5E9; }
.send-btn { width: 36px; height: 36px; border: none; border-radius: 10px; background: linear-gradient(135deg, #0EA5E9, #0284C7); color: white; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s; flex-shrink: 0; }
.send-btn:hover:not(:disabled) { box-shadow: 0 4px 12px -2px rgba(14,165,233,0.4); }
.send-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.send-btn svg { width: 16px; height: 16px; }
.stop-btn { background: linear-gradient(135deg, #EF4444, #DC2626); }
.stop-btn:hover { box-shadow: 0 4px 12px -2px rgba(239,68,68,0.4); }
.stop-btn svg { width: 14px; height: 14px; }
</style>
