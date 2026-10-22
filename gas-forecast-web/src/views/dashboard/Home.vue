<template>
  <main class="forecast-home">
    <header class="home-header">
      <div class="header-inner">
        <button class="brand" type="button" @click="router.push('/')">
          <span class="brand-logo">
            <svg viewBox="0 0 32 32" aria-hidden="true">
              <path d="M16 3S5 10 5 18.5a11 11 0 0 0 22 0C27 10 16 3 16 3Z" fill="currentColor" />
              <path d="M16 23V11m0 0-5 5m5-5 5 5" fill="none" stroke="#fff" stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" />
            </svg>
          </span>
          <span>天然气市场需求智能预测平台</span>
        </button>
        <nav class="forecast-nav" aria-label="中心切换">
          <RouterLink
            v-for="menu in topMenus"
            :key="menu.id"
            :to="topMenuTarget(menu)"
            :class="{ active: isAgentTopMenu(menu) }"
          >{{ topMenuName(menu) }}</RouterLink>
        </nav>
        <div class="account-actions">
          <span class="user-name">{{ userName }}</span>
          <button class="logout-button" type="button" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <section class="hero">
      <span class="hero-badge">AI Powered Forecasting</span>
      <h1>智能预测，驱动决策</h1>
      <p>基于机器学习与大模型分析，为天然气市场需求预测提供精准洞察</p>
    </section>

    <section class="cards" aria-label="预测工作台">
      <RouterLink
        v-for="(agent, index) in agents"
        :key="agent.id"
        class="agent-card"
        :style="{ '--accent': agent.accent, '--accent-light': agent.accentLight, animationDelay: `${index * 80}ms` }"
        :to="agent.path"
      >
        <span class="decoration decoration-large"></span>
        <span class="decoration decoration-small"></span>
        <div class="card-heading">
          <span class="card-icon">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path :d="agent.iconPath" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" />
            </svg>
          </span>
          <span class="card-tag">{{ agent.tag }}</span>
        </div>
        <h2>{{ agent.name }}</h2>
        <p>{{ agent.description }}</p>
        <div class="features">
          <span v-for="feature in agent.features" :key="feature">{{ feature }}</span>
        </div>
        <div class="card-footer">
          <strong>进入工作台</strong>
          <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M3 8h10m0 0L9 4m4 4-4 4" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" />
          </svg>
        </div>
      </RouterLink>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { clearToken, getProfile } from '@/utils/auth'
import type { AuthMenu } from '@/utils/auth'

const router = useRouter()
const userName = computed(() => getProfile().user?.realName || '管理员')
const topMenus = computed(() =>
  (getProfile().menus || [])
    .filter((menu) => !menu.hidden && menu.path !== '/' && Boolean(menu.children?.some((child) => !child.hidden)))
    .sort((left, right) => (left.sortNo || 0) - (right.sortNo || 0))
)
const firstMenuPath = (menu: AuthMenu): string => {
  if (menu.component && menu.path) return menu.path
  for (const child of menu.children || []) {
    const path = firstMenuPath(child)
    if (path) return path
  }
  return ''
}
const isAgentTopMenu = (menu: AuthMenu) =>
  menu.name === '智能体中心' ||
  menu.path === '/agent' ||
  Boolean(menu.children?.some((child) => child.path?.startsWith('/agent/')))
const topMenuTarget = (menu: AuthMenu) => isAgentTopMenu(menu) ? '/' : firstMenuPath(menu) || '/'
const topMenuName = (menu: AuthMenu) => menu.name === '后台数据管理' ? '数据管理' : menu.name

const agents = [
  {
    id: 'winter-supply', name: '冬季保供预测', path: '/agent/winter-supply', tag: '供需平衡',
    description: '针对冬季用气高峰，结合气温因素预测需求，评估供应缺口与保供策略',
    features: ['高峰预测', '供需分析', '缺口预警'], accent: '#0ea5e9', accentLight: '#e0f2fe',
    iconPath: 'M12 2 2 22h20L12 2Zm0 6v6m0 3v1'
  },
  {
    id: 'monthly-sales', name: '月度销量预测', path: '/agent/monthly-sales', tag: '趋势分析',
    description: '基于历史销量与季节性规律，精准预测未来月度天然气销量走势',
    features: ['季节分解', '销量预测', '同比分析'], accent: '#10b981', accentLight: '#d1fae5',
    iconPath: 'M3 3v18h18M7 14l4-4 4 4 6-6'
  },
  {
    id: 'short-term', name: '短期客户预测', path: '/agent/short-term', tag: '客户洞察',
    description: '面向重点客户，结合历史销量与天气特征预测短期用气量，识别客户用气趋势',
    features: ['客户分析', '短期预测', '趋势洞察'], accent: '#f59e0b', accentLight: '#fef3c7',
    iconPath: 'M17 21v-2c0-3-2-5-5-5s-5 2-5 5v2m5-10a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM5 21v-2c0-2 1-4 3-5m11 7v-2c0-2-1-4-3-5'
  }
]

const logout = () => {
  clearToken()
  void router.replace('/login')
}
</script>

<style scoped>
.forecast-home { --text: #0f172a; --muted: #64748b; min-height: 100vh; background: #f8fafc; color: var(--text); }
.home-header { height: 64px; border-bottom: 1px solid rgba(226, 232, 240, 0.8); background: rgba(255, 255, 255, 0.92); backdrop-filter: blur(16px); }
.header-inner { position: relative; width: calc(100% - 48px); height: 100%; margin: 0 auto; display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.brand { display: flex; align-items: center; gap: 10px; padding: 0; border: 0; background: none; color: #172033; font: inherit; font-size: 15px; font-weight: 650; cursor: pointer; }
.brand-logo { width: 32px; height: 32px; color: #0ea5e9; }
.brand-logo svg { width: 100%; height: 100%; }
.forecast-nav, .account-actions { display: flex; align-items: center; }
.forecast-nav { position: absolute; left: 50%; transform: translateX(-50%); gap: 34px; padding: 9px 18px; }
.account-actions { margin-left: auto; gap: 16px; }
.forecast-nav a { display: flex; align-items: center; justify-content: center; padding: 9px 16px; border-radius: 9px; color: #64748b; font-size: 15px; text-decoration: none; white-space: nowrap; }
.forecast-nav a:hover { color: #0f172a; }
.forecast-nav a.active { background: linear-gradient(135deg, #0ea5e9, #0284c7); color: #fff; font-weight: 600; box-shadow: 0 5px 14px -5px rgba(14, 165, 233, 0.55); }
.management-link, .logout-button { border: 0; background: none; color: #475569; font-size: 12px; text-decoration: none; cursor: pointer; }
.management-link:hover, .logout-button:hover { color: #0284c7; }
.user-name { color: #64748b; font-size: 12px; }
.hero { padding: 82px 24px 58px; text-align: center; }
.hero-badge { display: inline-flex; padding: 5px 13px; border: 1px solid #bae6fd; border-radius: 999px; background: #f0f9ff; color: #0284c7; font-size: 12px; font-weight: 600; }
.hero h1 { margin: 20px 0 14px; font-size: clamp(32px, 4vw, 44px); line-height: 1.15; letter-spacing: -0.04em; }
.hero p { margin: 0; color: var(--muted); font-size: 15px; }
.cards { width: min(1020px, calc(100% - 48px)); margin: 0 auto; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
.agent-card { position: relative; overflow: hidden; min-height: 292px; padding: 28px 24px 22px; border: 1px solid #edf1f5; border-radius: 18px; background: #fff; color: var(--text); text-decoration: none; box-shadow: 0 8px 28px rgba(15, 23, 42, 0.035); transition: transform 0.25s ease, border-color 0.25s ease, box-shadow 0.25s ease; animation: card-enter 0.5s ease backwards; }
.agent-card:hover { transform: translateY(-5px); border-color: var(--accent); box-shadow: 0 18px 38px rgba(15, 23, 42, 0.09); }
@keyframes card-enter { from { opacity: 0; transform: translateY(16px); } }
.decoration { position: absolute; border-radius: 50%; background: var(--accent-light); pointer-events: none; }
.decoration-large { width: 105px; height: 105px; top: -38px; right: -30px; opacity: 0.55; }
.decoration-small { width: 50px; height: 50px; top: 44px; right: 8px; opacity: 0.38; }
.card-heading { position: relative; display: flex; align-items: center; justify-content: space-between; margin-bottom: 21px; }
.card-icon { width: 48px; height: 48px; display: grid; place-items: center; border-radius: 13px; background: var(--accent); color: #fff; }
.card-icon svg { width: 24px; height: 24px; }
.card-tag { padding: 5px 10px; border-radius: 7px; background: var(--accent-light); color: var(--accent); font-size: 11px; font-weight: 600; }
.agent-card h2 { position: relative; margin: 0 0 9px; font-size: 18px; }
.agent-card > p { position: relative; min-height: 44px; margin: 0 0 16px; color: var(--muted); font-size: 13px; line-height: 1.7; }
.features { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 21px; }
.features span { padding: 4px 9px; border: 1px solid #f1f5f9; border-radius: 6px; background: #f8fafc; color: #475569; font-size: 11px; }
.card-footer { display: flex; align-items: center; gap: 6px; padding-top: 16px; border-top: 1px solid #f1f5f9; color: var(--accent); font-size: 13px; }
.card-footer svg { width: 16px; height: 16px; transition: transform 0.2s; }
.agent-card:hover .card-footer svg { transform: translateX(3px); }
@media (max-width: 1050px) { .forecast-nav, .user-name { display: none; } }
@media (max-width: 760px) {
  .header-inner { width: calc(100% - 28px); }
  .brand { font-size: 13px; }
  .management-link { display: none; }
  .hero { padding: 58px 20px 42px; }
  .cards { width: calc(100% - 32px); grid-template-columns: 1fr; padding-bottom: 32px; }
}
</style>
