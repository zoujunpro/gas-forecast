<template>
  <div class="home">
    <!-- Hero区域 -->
    <section class="hero">
      <div class="hero-badge">AI Powered Forecasting</div>
      <h1 class="hero-title">智能预测，驱动决策</h1>
      <p class="hero-subtitle">基于机器学习与大模型分析，为天然气市场需求预测提供精准洞察</p>
    </section>

    <!-- 智能体卡片 -->
    <section class="cards-section">
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
                <path :d="agent.iconPath" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
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
                <path d="M3 8H13M13 8L9 4M13 8L9 12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

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
    iconPath: 'M17 21V19C17 16 15 14 12 14C9 14 7 16 7 19V21 M12 11C13.66 11 15 9.66 15 8C15 6.34 13.66 5 12 5C10.34 5 9 6.34 9 8C9 9.66 10.34 11 12 11Z M5 21V19C5 17 6 15 8 14 M19 21V19C19 17 18 15 16 14'
  }
]

const goToAgent = (agentId: string) => {
  void router.push(`/agent/${agentId}`)
}
</script>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
  padding: 48px 32px 64px;
}

/* Hero */
.hero {
  text-align: center;
  padding: 40px 0 56px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  background: #F0F9FF;
  border: 1px solid #BAE6FD;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 600;
  color: #0284C7;
  letter-spacing: 0.02em;
  margin-bottom: 20px;
}

.hero-title {
  font-size: 40px;
  font-weight: 700;
  color: #0F172A;
  letter-spacing: -0.03em;
  line-height: 1.1;
  margin-bottom: 14px;
}

.hero-subtitle {
  font-size: 16px;
  color: #64748B;
  line-height: 1.6;
  max-width: 520px;
  margin: 0 auto;
}

/* Cards */
.cards-section {
  margin-bottom: 56px;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.agent-card {
  position: relative;
  background: white;
  border: 1px solid #F1F5F9;
  border-radius: 20px;
  padding: 28px 24px 24px;
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
  box-shadow: 0 12px 32px -8px rgba(0, 0, 0, 0.08), 0 4px 12px -4px rgba(0, 0, 0, 0.04);
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
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
  letter-spacing: 0.02em;
}

/* Content */
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #0F172A;
  letter-spacing: -0.02em;
  margin-bottom: 8px;
}

.card-desc {
  font-size: 13px;
  color: #64748B;
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
  font-size: 11px;
  font-weight: 500;
  color: #475569;
  background: #F8FAFC;
  border: 1px solid #F1F5F9;
  padding: 4px 10px;
  border-radius: 6px;
}

/* Footer */
.card-footer {
  padding-top: 16px;
  border-top: 1px solid #F1F5F9;
}

.card-action {
  display: flex;
  align-items: center;
  gap: 6px;
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
  border-color: #0EA5E9;
}

.theme-sky .card-icon {
  background: #0EA5E9;
}

.theme-sky .deco-circle,
.theme-sky .card-tag {
  background: #E0F2FE;
}

.theme-sky .card-tag,
.theme-sky .card-action {
  color: #0EA5E9;
}

.theme-green:hover {
  border-color: #10B981;
}

.theme-green .card-icon {
  background: #10B981;
}

.theme-green .deco-circle,
.theme-green .card-tag {
  background: #D1FAE5;
}

.theme-green .card-tag,
.theme-green .card-action {
  color: #10B981;
}

.theme-amber:hover {
  border-color: #F59E0B;
}

.theme-amber .card-icon {
  background: #F59E0B;
}

.theme-amber .deco-circle,
.theme-amber .card-tag {
  background: #FEF3C7;
}

.theme-amber .card-tag,
.theme-amber .card-action {
  color: #F59E0B;
}

.arrow-svg {
  width: 16px;
  height: 16px;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* Responsive */
@media (max-width: 900px) {
  .cards-grid {
    grid-template-columns: 1fr;
  }
  .hero-title {
    font-size: 30px;
  }
}
</style>
