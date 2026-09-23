<template>
  <div class="home">
    <!-- Hero区域 -->
    <section class="hero">
      <div class="hero-content">
        <p class="hero-eyebrow">NATURAL GAS FORECASTING</p>
        <h1 class="hero-title">天然气预测平台</h1>
        <p class="hero-subtitle">数据驱动 · 智能预测 · 科学决策 · 保障能源安全</p>
        <div class="hero-capabilities" aria-label="平台核心能力">
          <span v-for="capability in capabilities" :key="capability">{{ capability }}</span>
        </div>
      </div>
      <div class="hero-slogan">
        <strong>清洁能源 · 智慧未来</strong>
        <span>用数据点亮能源安全</span>
      </div>
    </section>

    <!-- 智能体卡片 -->
    <section class="cards-section">
      <div class="section-heading">
        <h2>智能体中心</h2>
        <p>选择对应的智能体，开始您的预测分析之旅</p>
      </div>
      <div class="cards-grid">
        <article
          v-for="agent in agents"
          :key="agent.id"
          :class="['agent-card', agent.theme, agent.animationClass]"
          @click="goToAgent(agent.id)"
        >
          <!-- 顶部装饰 -->
          <div class="card-decoration">
            <div class="deco-circle deco-1"></div>
            <div class="deco-circle deco-2"></div>
          </div>

          <!-- 图标 -->
          <div class="card-icon-wrap">
            <div class="card-icon">
              <svg viewBox="0 0 24 24" fill="none" class="icon-svg">
                <path
                  :d="agent.iconPath"
                  stroke="white"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </div>
            <span class="card-tag">{{ agent.tag }}</span>
          </div>

          <!-- 内容 -->
          <h3 class="card-title">{{ agent.name }}</h3>
          <p class="card-desc">{{ agent.description }}</p>

          <!-- 特性标签 -->
          <div class="card-features">
            <span v-for="feature in agent.features" :key="feature" class="feature-chip">
              {{ feature }}
            </span>
          </div>

          <!-- 底部 -->
          <div class="card-footer">
            <span class="card-action">
              进入工作台
              <svg class="arrow-svg" viewBox="0 0 16 16" fill="none">
                <path
                  d="M3 8H13M13 8L9 4M13 8L9 12"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
          </div>
        </article>
      </div>
    </section>

    <section class="stats-section">
      <div class="section-heading compact-heading">
        <h2>平台数据概览</h2>
        <p>平台当前接入的基础数据与模型资源</p>
      </div>
      <div class="stats-grid" v-loading="statsLoading">
        <article v-for="stat in platformStats" :key="stat.label" class="stat-item" :class="stat.tone">
          <div class="stat-icon">
            <el-icon><component :is="stat.icon" /></el-icon>
          </div>
          <div>
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, Cpu, DataBoard, Grid, Location, User } from '@element-plus/icons-vue'
import { listPage } from '@/api/management'

const router = useRouter()
const capabilities = ['需求预测', '安全保供', '能源分析', '智能决策']
const statsLoading = ref(false)
const stats = ref({
  regions: null as number | null,
  industries: null as number | null,
  customers: null as number | null,
  history: null as number | null,
  models: null as number | null,
  latestUpdate: ''
})

const formatCount = (value: number | null, unit = ' 个') =>
  value === null ? '--' : `${value.toLocaleString('zh-CN')}${unit}`

const platformStats = computed(() => [
  { label: '覆盖地区', value: formatCount(stats.value.regions), icon: Location, tone: 'tone-blue' },
  { label: '行业数量', value: formatCount(stats.value.industries), icon: Grid, tone: 'tone-cyan' },
  { label: '客户数量', value: formatCount(stats.value.customers), icon: User, tone: 'tone-blue' },
  { label: '历史数据', value: formatCount(stats.value.history, ' 条'), icon: DataBoard, tone: 'tone-indigo' },
  { label: '模型数量', value: formatCount(stats.value.models), icon: Cpu, tone: 'tone-purple' },
  { label: '最近更新', value: stats.value.latestUpdate || '--', icon: Calendar, tone: 'tone-violet' }
])

const loadPlatformStats = async () => {
  statsLoading.value = true
  const requests = await Promise.allSettled([
    listPage('/base-region', { page: 1, size: 1 }),
    listPage('/base-industry', { page: 1, size: 1 }),
    listPage('/base-customer', { page: 1, size: 1 }),
    listPage('/data-daily-sales', { page: 1, size: 1 }),
    listPage('/data-monthly-sales', { page: 1, size: 1 }),
    listPage('/model-config', { page: 1, size: 1 }),
    listPage('/model-forecast-record', { page: 1, size: 1 })
  ])
  const page = (index: number) => (requests[index].status === 'fulfilled' ? requests[index].value : null)
  const daily = page(3)?.total ?? null
  const monthly = page(4)?.total ?? null
  const latestRecord = page(6)?.records?.[0]
  stats.value = {
    regions: page(0)?.total ?? null,
    industries: page(1)?.total ?? null,
    customers: page(2)?.total ?? null,
    history: daily === null && monthly === null ? null : (daily || 0) + (monthly || 0),
    models: page(5)?.total ?? null,
    latestUpdate: String(latestRecord?.updatedAt || latestRecord?.createdAt || '').slice(0, 10)
  }
  statsLoading.value = false
}

onMounted(loadPlatformStats)

type AgentTheme = 'theme-sky' | 'theme-green' | 'theme-amber'
type AnimationClass = 'delay-0' | 'delay-1' | 'delay-2'

interface Agent {
  id: string
  name: string
  tag: string
  description: string
  features: string[]
  theme: AgentTheme
  animationClass: AnimationClass
  iconPath: string
}

const agents: Agent[] = [
  {
    id: 'winter-supply',
    name: '冬季保供预测',
    tag: '供需平衡',
    description: '针对冬季用气高峰，结合气温因素预测需求，评估供应缺口与保供策略',
    features: ['高峰预测', '供需分析', '缺口预警'],
    theme: 'theme-sky',
    animationClass: 'delay-0',
    iconPath: 'M12 2L2 22H22L12 2Z M12 8V14 M12 17V18'
  },
  {
    id: 'monthly-sales',
    name: '月度销量预测',
    tag: '趋势分析',
    description: '基于历史销量与季节性规律，精准预测未来月度天然气销量走势',
    features: ['季节分解', '销量预测', '同比分析'],
    theme: 'theme-green',
    animationClass: 'delay-1',
    iconPath: 'M3 3V21H21 M7 14L11 10L15 14L21 8'
  },
  {
    id: 'short-term',
    name: '短期客户预测',
    tag: '客户洞察',
    description: '面向重点客户，利用随机森林模型预测短期用气量，识别客户行为模式',
    features: ['客户聚类', '短期预测', '行为分析'],
    theme: 'theme-amber',
    animationClass: 'delay-2',
    iconPath:
      'M17 21V19C17 16 15 14 12 14C9 14 7 16 7 19V21 M12 11C13.66 11 15 9.66 15 8C15 6.34 13.66 5 12 5C10.34 5 9 6.34 9 8C9 9.66 10.34 11 12 11Z M5 21V19C5 17 6 15 8 14 M19 21V19C19 17 18 15 16 14'
  }
]

const goToAgent = (agentId: string) => {
  void router.push(`/agent/${agentId}`)
}
</script>

<style scoped>
.home {
  width: calc(100% + 28px);
  margin: -14px -14px 0;
  padding-bottom: 36px;
}

/* Hero */
.hero {
  position: relative;
  min-height: 350px;
  margin-bottom: 0;
  overflow: hidden;
  border: none;
  border-radius: 0;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.98) 0%, rgba(246, 251, 255, 0.9) 32%, rgba(255, 255, 255, 0) 58%),
    url('@/assets/home-hero-lng-v2.webp') center / cover no-repeat;
  box-shadow: 0 8px 24px rgba(27, 91, 150, 0.06);
}

.hero-content {
  position: relative;
  z-index: 1;
  width: min(590px, 55%);
  padding: 58px 0 42px 54px;
}

.hero-eyebrow {
  margin-bottom: 10px;
  color: #1677ff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.hero-title {
  margin-bottom: 12px;
  color: #0b2f6b;
  font-size: clamp(34px, 3vw, 48px);
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: 0.02em;
}

.hero-subtitle {
  color: #405b80;
  font-size: 17px;
  line-height: 1.7;
  letter-spacing: 0.04em;
}

.hero-capabilities {
  display: grid;
  grid-template-columns: repeat(4, auto);
  justify-content: start;
  gap: 0;
  margin-top: 32px;
}

.hero-capabilities span {
  position: relative;
  padding: 0 18px;
  color: #344f73;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.hero-capabilities span:first-child {
  padding-left: 0;
}

.hero-capabilities span:not(:last-child)::after {
  position: absolute;
  top: 50%;
  right: 0;
  width: 1px;
  height: 14px;
  background: #c8d9eb;
  content: '';
  transform: translateY(-50%);
}

.hero-slogan {
  position: absolute;
  z-index: 1;
  top: 30px;
  right: 34px;
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 4px;
  color: #ffffff;
  text-shadow: 0 1px 5px rgba(15, 66, 116, 0.35);
}

.hero-slogan strong {
  font-size: 18px;
  font-weight: 500;
  letter-spacing: 0.14em;
}

.hero-slogan span {
  font-size: 13px;
  letter-spacing: 0.08em;
}

/* Cards */
.cards-section {
  padding: 28px 24px 0;
}

.section-heading {
  margin-bottom: 16px;
}

.section-heading h2 {
  margin: 0 0 4px;
  color: #1d2129;
  font-size: 20px;
  font-weight: 600;
}

.section-heading p {
  margin: 0;
  color: #86909c;
  font-size: 13px;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.agent-card {
  position: relative;
  background: white;
  border: 1px solid #f1f5f9;
  border-radius: 10px;
  padding: 22px 20px 18px;
  cursor: pointer;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  animation: cardEnter 0.6s cubic-bezier(0.4, 0, 0.2, 1) backwards;
}

@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.agent-card:hover {
  transform: translateY(-4px);
  box-shadow:
    0 12px 32px -8px rgba(0, 0, 0, 0.08),
    0 4px 12px -4px rgba(0, 0, 0, 0.04);
}

.agent-card:hover .card-decoration .deco-1 {
  transform: scale(1.2) translate(10px, -10px);
  opacity: 0.6;
}

.agent-card:hover .card-icon {
  transform: scale(1.05);
}

.agent-card:hover .arrow-svg {
  transform: translateX(3px);
}

/* Decoration */
.card-decoration {
  position: absolute;
  top: 0;
  right: 0;
  width: 140px;
  height: 140px;
  overflow: hidden;
  pointer-events: none;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.deco-1 {
  width: 100px;
  height: 100px;
  top: -30px;
  right: -30px;
  opacity: 0.4;
}

.deco-2 {
  width: 50px;
  height: 50px;
  top: 50px;
  right: 10px;
  opacity: 0.3;
}

/* Icon */
.card-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.icon-svg {
  width: 24px;
  height: 24px;
}

.card-tag {
  font-size: 13px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
  letter-spacing: 0.02em;
}

/* Content */
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
  letter-spacing: -0.02em;
  margin-bottom: 8px;
}

.card-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  margin-bottom: 16px;
  min-height: 42px;
}

/* Features */
.card-features {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
}

.feature-chip {
  font-size: 13px;
  font-weight: 500;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  padding: 4px 10px;
  border-radius: 6px;
}

/* Footer */
.card-footer {
  padding-top: 4px;
}

.card-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 38px;
  border-radius: 6px;
  color: #ffffff !important;
  font-size: 13px;
  font-weight: 600;
  transition: gap 0.3s;
}

.delay-0 {
  animation-delay: 0s;
}

.delay-1 {
  animation-delay: 0.1s;
}

.delay-2 {
  animation-delay: 0.2s;
}

.theme-sky:hover {
  border-color: #0ea5e9;
}

.theme-sky .card-icon {
  background: #0ea5e9;
}

.theme-sky .deco-circle,
.theme-sky .card-tag {
  background: #e0f2fe;
}

.theme-sky .card-tag,
.theme-sky .card-tag {
  color: #0ea5e9;
}

.theme-sky .card-action {
  background: #1677ff;
}

.theme-green:hover {
  border-color: #10b981;
}

.theme-green .card-icon {
  background: #10b981;
}

.theme-green .deco-circle,
.theme-green .card-tag {
  background: #d1fae5;
}

.theme-green .card-tag,
.theme-green .card-tag {
  color: #10b981;
}

.theme-green .card-action {
  background: #0fb89f;
}

.theme-amber:hover {
  border-color: #7f56d9;
}

.theme-amber .card-icon {
  background: #7f56d9;
}

.theme-amber .deco-circle,
.theme-amber .card-tag {
  background: #f4f3ff;
}

.theme-amber .card-tag {
  color: #7f56d9;
}

.theme-amber .card-action {
  background: #7f56d9;
}

.arrow-svg {
  width: 16px;
  height: 16px;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* Platform stats */
.stats-section {
  margin: 20px 24px 0;
  padding: 18px 20px 20px;
  border: 1px solid #e6edf7;
  border-radius: 10px;
  background: linear-gradient(135deg, #f7fbff 0%, #f4f7ff 100%);
}

.compact-heading {
  margin-bottom: 14px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid rgba(224, 233, 244, 0.8);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
}

.stat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border-radius: 8px;
  background: #eaf3ff;
  color: #1677ff;
  font-size: 18px;
}

.stat-item > div:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.stat-item span {
  color: #86909c;
  font-size: 13px;
}

.stat-item strong {
  overflow: hidden;
  color: #1d2129;
  font-size: 17px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tone-cyan .stat-icon {
  background: #e6fffb;
  color: #0fb89f;
}

.tone-indigo .stat-icon,
.tone-violet .stat-icon {
  background: #f0f3ff;
  color: #6172f3;
}

.tone-purple .stat-icon {
  background: #f4f3ff;
  color: #7f56d9;
}

/* Responsive */
@media (max-width: 900px) {
  .home {
    width: calc(100% + 28px);
    padding: 0 0 28px;
  }
  .hero {
    min-height: 330px;
    background-position: 62% center;
  }
  .hero-content {
    width: 72%;
    padding: 48px 24px 34px;
  }
  .hero-capabilities {
    grid-template-columns: repeat(2, auto);
    gap: 12px 0;
  }
  .hero-capabilities span:nth-child(2)::after {
    display: none;
  }
  .hero-slogan {
    display: none;
  }
  .cards-grid {
    grid-template-columns: 1fr;
  }
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .hero-title {
    font-size: 30px;
  }
}

@media (max-width: 560px) {
  .hero {
    min-height: 360px;
    background-position: 68% center;
  }
  .hero::after {
    position: absolute;
    inset: 0;
    background: rgba(255, 255, 255, 0.28);
    content: '';
  }
  .hero-content {
    width: 100%;
    padding: 38px 22px;
  }
  .hero-subtitle {
    max-width: 320px;
    font-size: 15px;
  }
  .cards-section {
    padding: 22px 14px 0;
  }
  .stats-section {
    margin: 16px 14px 0;
  }
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
