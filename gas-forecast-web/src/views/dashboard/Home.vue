<template>
  <main class="forecast-home">
    <AppHeader
      :menus="topMenus"
      :target="topMenuTarget"
      :label="topMenuName"
      :active="isAgentTopMenu"
      :welcome-name="userName"
      @home="router.push('/')"
      @logout="logout"
    />
    <section class="hero"><img class="hero-background" src="/assets/dashboard/banner-background.png" alt="" /><div class="hero-copy"><p class="eyebrow">AI POWERED FORECASTING</p><h1>智能预测，<span>驱动决策</span></h1><p>面向天然气需求，提供预测与结果分析</p></div><div class="capabilities" aria-label="平台能力"><article v-for="item in capabilities" :key="item.title" class="capability"><img :src="item.image" alt="" /><span><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span></article></div></section>
    <section class="dashboard-main">
      <section class="summary" aria-label="平台汇总"><article v-for="metric in metrics" :key="metric.label" class="metric"><img :src="metric.image" alt="" /><div><h3>{{ metric.label }}</h3><strong v-if="dashboardReady">{{ metric.value }}</strong><span v-else class="metric-value-skeleton" aria-label="数据加载中"></span><span class="unit">{{ metric.unit }}</span><small v-if="dashboardReady && metric.detail" v-html="metric.detail"></small><small v-else-if="!dashboardReady" class="metric-detail-skeleton"></small></div></article></section>
      <div class="section-head"><h2>业务智能体</h2></div>
      <section class="agents" aria-label="业务智能体"><article v-for="agent in agents" :key="agent.id" class="agent-card" :class="agent.theme"><img class="card-art" :src="agent.decoration" alt="" /><div class="card-top"><img class="agent-icon" :src="agent.image" alt="" /><div><h3>{{ agent.name }}</h3><p class="scope">{{ agent.scope }}</p></div></div><p class="description">{{ agent.description }}</p><div class="tags"><span v-for="tag in agent.tags" :key="tag">{{ tag }}</span></div><div class="card-bottom"><RouterLink class="enter" :to="agent.path">进入智能体 <b>→</b></RouterLink></div></article></section>
      <div class="bottom"><section class="panel"><div class="panel-head"><h2>最近预测</h2><a class="link" href="#recent">查看全部 →</a></div><div class="table-wrap"><table><thead><tr><th>预测名称</th><th>智能体</th><th>业务范围</th><th>预测区间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in predictions" :key="item.forecastNo"><td>{{ item.name }}</td><td>{{ item.agent }}</td><td>{{ item.scope }}</td><td>{{ item.period }}</td><td><span class="status" :class="{ running: item.running }">{{ item.status }}</span></td><td><a class="link" href="#result">{{ item.running ? '查看进度' : '查看结果' }}</a></td></tr><tr v-for="row in (predictionsReady ? 0 : 3)" :key="`loading-${row}`" class="prediction-skeleton"><td v-for="cell in 6" :key="cell"><span></span></td></tr><tr v-if="predictionsReady && !predictions.length"><td class="empty-predictions" colspan="6">暂无预测记录</td></tr></tbody></table></div></section><section class="panel workflow-panel"><div class="panel-head"><h2>智能体如何工作</h2><span class="flow-label">业务流程说明</span></div><ol class="workflow"><li v-for="(item,index) in workflow" :key="item.title"><b>{{ index + 1 }}</b><span><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span></li></ol></section></div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { clearToken, getProfile } from '@/utils/auth'
import type { AuthMenu } from '@/utils/auth'
import AppHeader from '@/components/AppHeader.vue'
import { listPage } from '@/api/management'
const router = useRouter()
const userName = computed(() => getProfile().user?.realName || '管理员')
const topMenus = computed(() => (getProfile().menus || []).filter((m) => !m.hidden && m.path !== '/' && Boolean(m.children?.some((c) => !c.hidden))).sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0)))
const firstMenuPath = (m: AuthMenu): string => m.component && m.path ? m.path : (m.children || []).map(firstMenuPath).find(Boolean) || ''
const isAgentTopMenu = (m: AuthMenu) => m.name === '智能体中心' || m.path === '/agent' || Boolean(m.children?.some((c) => c.path?.startsWith('/agent/')))
const topMenuTarget = (m: AuthMenu) => isAgentTopMenu(m) ? '/' : firstMenuPath(m) || '/'
const topMenuName = (m: AuthMenu) => m.name === '后台数据管理' ? '数据管理' : m.name
const dashboard = ref({ customers: 0, regions: 0, industries: 0, agents: 0, todayPredictions: 0, runningPredictions: 0, completedPredictions: 0 })
const dashboardCacheKey = 'gas-forecast-dashboard-summary-v2'
const dashboardReady = ref(false)
const restoreDashboardCache = () => {
  try {
    const cached = sessionStorage.getItem(dashboardCacheKey)
    if (!cached) return
    const data = JSON.parse(cached)
    if (data && typeof data === 'object') {
      dashboard.value = { ...dashboard.value, ...data }
      dashboardReady.value = true
    }
  } catch { /* Ignore invalid cache and show the loading placeholder. */ }
}
restoreDashboardCache()
const metrics = computed(() => [
  { label:'客户总数',value:String(dashboard.value.customers),unit:'家',image:'/assets/dashboard/stat-customers.png' },
  { label:'覆盖地区',value:String(dashboard.value.regions),unit:'个',image:'/assets/dashboard/stat-regions.png' },
  { label:'覆盖行业',value:String(dashboard.value.industries),unit:'类',image:'/assets/dashboard/stat-industries.png' },
  { label:'业务智能体',value:String(dashboard.value.agents),unit:'个',image:'/assets/dashboard/stat-agents.png' },
  { label:'今日预测',value:String(dashboard.value.todayPredictions),unit:'次',image:'/assets/dashboard/stat-today-tasks.png',detail:`预测中 <b>${dashboard.value.runningPredictions}</b> · 已完成 ${dashboard.value.completedPredictions}` }
])
const capabilities = [
  { title: '多源数据融合', description: '气象 · 用气 · 经济 · 行业', image: '/assets/dashboard/capability-data-fusion.png' },
  { title: 'AI 智能分析', description: '自然语言 · 智能解读', image: '/assets/dashboard/capability-ai-analysis.png' },
  { title: '业务场景覆盖', description: '保供 · 经营 · 客户', image: '/assets/dashboard/capability-business-scenes.png' },
  { title: '支持决策管理', description: '预测 · 分析 · 建议', image: '/assets/dashboard/capability-decision-support.png' }
]
const workflow = [
  { title: '需求理解', description: '识别业务意图' },
  { title: '上下文感知', description: '补全任务信息' },
  { title: '数据感知', description: '检查数据可用' },
  { title: '智能预测', description: '自动回测择优' },
  { title: '结果洞察', description: '解读预测趋势' },
  { title: '智能分析', description: '分析异常原因' }
]
const agents = [{id:'winter',name:'冬季保供预测',scope:'省级 · 旬度',description:'基于气温与历史数据，预测各省份冬季天然气需求，辅助保供决策。',tags:['高峰预测','供需分析','缺口预警'],path:'/agent/winter-supply',image:'/assets/dashboard/winter-icon.png',decoration:'/assets/dashboard/winter-decoration.png',theme:'blue'},{id:'monthly',name:'月度销量预测',scope:'省份＋行业 · 月度',description:'基于历史销量与季节性规律，精确预测未来月度天然气销量走势。',tags:['季节分解','销量预测','同比分析'],path:'/agent/monthly-sales',image:'/assets/dashboard/monthly-icon.png',decoration:'/assets/dashboard/monthly-decoration.png',theme:'green'},{id:'short',name:'短期客户预测',scope:'客户 · 日度',description:'面向重点客户，结合历史销量与天气特征，预测短期用气量，识别客户用气趋势。',tags:['客户分析','短期预测','趋势洞察'],path:'/agent/short-term',image:'/assets/dashboard/customer-icon.png',decoration:'/assets/dashboard/customer-decoration.png',theme:'orange'}]
const predictions = ref<Array<{ forecastNo: string; name: string; agent: string; scope: string; period: string; status: string; running: boolean }>>([])
const predictionsCacheKey = 'gas-forecast-dashboard-predictions'
const predictionsReady = ref(false)
const restorePredictionsCache = () => {
  try {
    const cached = sessionStorage.getItem(predictionsCacheKey)
    if (!cached) return
    const data = JSON.parse(cached)
    if (Array.isArray(data)) {
      predictions.value = data
      predictionsReady.value = true
    }
  } catch { /* Ignore invalid cache and show the table placeholder. */ }
}
restorePredictionsCache()
const agentName = (code?: string) => ({ 'winter-supply': '冬季保供', 'monthly-sales': '月度销量', 'short-term': '短期客户', general: '通用智能助手' }[String(code || '').toLowerCase()] || code || '-')
const statusName = (status?: string) => ({ SUCCESS: '已完成', RUNNING: '运行中', PENDING: '等待中', FAILED: '失败' }[String(status || '').toUpperCase()] || status || '-')
const isToday = (value?: string) => {
  if (!value) return false
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return false
  const today = new Date()
  return date.getFullYear() === today.getFullYear() && date.getMonth() === today.getMonth() && date.getDate() === today.getDate()
}
const loadDashboard = async () => {
  const results = await Promise.allSettled([
    listPage('/base-customer', { page: 1, size: 1 }),
    listPage('/base-region', { page: 1, size: 1 }),
    listPage('/base-industry', { page: 1, size: 1 }),
    listPage('/agent-strategy-config', { page: 1, size: 1 }),
    listPage<any>('/model-execute-record', { page: 1, size: 500 })
  ])
  const total = (index: number) => results[index].status === 'fulfilled' ? Number(results[index].value.total || 0) : 0
  const records = results[4].status === 'fulfilled' ? results[4].value.records : []
  const todayRecords = records.filter((row: any) => isToday(row.createdAt || row.startedAt))
  dashboard.value = {
    customers: total(0), regions: total(1), industries: total(2), agents: total(3),
    todayPredictions: todayRecords.length,
    runningPredictions: todayRecords.filter((row: any) => ['RUNNING', 'PENDING'].includes(String(row.status).toUpperCase())).length,
    completedPredictions: todayRecords.filter((row: any) => String(row.status).toUpperCase() === 'SUCCESS').length
  }
  dashboardReady.value = true
  try { sessionStorage.setItem(dashboardCacheKey, JSON.stringify(dashboard.value)) } catch { /* Storage may be unavailable. */ }
  predictions.value = records.slice(0, 3).map((row: any) => {
    const scope = [row.regionName || row.regionCode, row.industryName || row.industryCode, row.customerName || row.customerCode].filter(Boolean).join(' / ') || '-'
    const name = row.customerName ? `${row.customerName}需求预测` : (scope === '-' ? row.forecastNo || '预测任务' : `${scope}需求预测`)
    const period = row.forecastStartDate ? `${row.forecastStartDate} 起，${row.forecastHorizon ?? '-'} ${row.forecastUnit || ''}` : '-'
    const status = String(row.status || '').toUpperCase()
    return { forecastNo: row.forecastNo || String(row.id), name, agent: agentName(row.agentCode), scope, period, status: statusName(status), running: ['RUNNING', 'PENDING'].includes(status) }
  })
  predictionsReady.value = true
  try { sessionStorage.setItem(predictionsCacheKey, JSON.stringify(predictions.value)) } catch { /* Storage may be unavailable. */ }
}
const logout = () => { clearToken(); void router.replace('/login') }
onMounted(() => { void loadDashboard() })
</script>

<style scoped>
.forecast-home{--blue:#0075ff;--ink:#081333;--muted:#596b90;--border:#e8f0fa;min-height:100vh;background:#f5faff;color:var(--ink);font-family:"Microsoft YaHei","Noto Sans CJK SC",Arial,sans-serif;font-size:16px}.home-header{height:73px;display:flex;align-items:center;justify-content:space-between;padding:0 24px;border-bottom:1px solid var(--border);background:linear-gradient(100deg,#fff,#fafdff)}.brand{display:flex;align-items:center;gap:12px;padding:0;border:0;background:none;color:var(--ink);font-family:"Microsoft YaHei","Noto Sans CJK SC",Arial,sans-serif;font-size:22px;font-weight:700;line-height:1.2;cursor:pointer;white-space:nowrap}.brand-logo{width:38px;height:46px;object-fit:contain}.forecast-nav{display:flex;gap:42px;align-items:center}.forecast-nav a{padding:14px 25px;border-radius:10px;color:#677792;text-decoration:none;font-size:17px;line-height:20px}.forecast-nav .active{background:linear-gradient(110deg,#0784ff,#0069ff);box-shadow:0 5px 10px #0075ff25;color:#fff;font-weight:700}.account-actions{display:flex;align-items:center;gap:16px;font-size:16px}.notice{width:34px;height:38px;object-fit:contain}.avatar{width:46px;height:46px;object-fit:contain}.user-info small{display:block;color:#697995;font-size:12px}.logout-button{border:0;background:none;color:#60769f;cursor:pointer;font:inherit}
.hero{position:relative;height:199px;overflow:hidden}.hero-background{position:absolute;inset:0;display:block;width:100%;height:100%;object-fit:fill;z-index:0}.hero:before{z-index:1;position:absolute;inset:0 55% 0 0;background:linear-gradient(90deg,#eff8ff 85%,#eff8ff00);content:""}.hero-copy{position:relative;z-index:2;padding:30px 0 0 6.65%;width:65%}.eyebrow{margin:0 0 4px;color:#368cff;font-size:15px;letter-spacing:1.2px}.hero h1{margin:0 0 3px;font-size:48px;line-height:1.3;letter-spacing:1px;font-weight:700}.hero h1 span{color:#0878ff}.hero-copy>p:last-child{margin:0;font-size:22px;font-weight:500}
.dashboard-main{width:92.4%;max-width:1800px;margin:0 auto;padding-bottom:16px}.summary{display:grid;height:109px;grid-template-columns:repeat(5,1fr);border:1px solid #edf4fc;border-radius:14px;background:#ffffffed;box-shadow:0 3px 12px #416e9f0b}.metric{position:relative;display:flex;align-items:center;gap:24px;padding:13px 24px}.metric+.metric:before{position:absolute;left:0;width:1px;height:56px;background:#e3edf9;content:""}.metric>img{width:78px;height:76px;object-fit:contain;border-radius:16px}.metric h3{margin:0;font-size:17px;font-weight:600;white-space:nowrap}.metric strong{font-size:38px;line-height:1.2}.unit{margin-left:8px;color:var(--muted);font-size:18px}.metric-value-skeleton,.metric-detail-skeleton{display:inline-block;border-radius:5px;background:linear-gradient(90deg,#edf3fb 25%,#f8fbff 45%,#edf3fb 65%);background-size:250% 100%;animation:metric-loading 1.25s ease-in-out infinite}.metric-value-skeleton{width:44px;height:36px;vertical-align:middle}.metric-detail-skeleton{display:block;width:122px;height:14px;margin-top:6px}.metric small{display:block;color:var(--muted);font-size:14px;white-space:nowrap}.metric small :deep(b){color:var(--blue);font-weight:500}@keyframes metric-loading{0%{background-position:100% 0}100%{background-position:-100% 0}}.section-head{display:flex;height:53px;align-items:center;justify-content:space-between}.section-head h2,.panel h2{display:flex;align-items:center;gap:15px;margin:0;font-size:21px}.section-head h2:before,.panel h2:before{display:block;width:7px;height:22px;border-radius:5px;background:linear-gradient(#006aff,#1d9bff);content:""}.section-head small{align-self:flex-start;margin-top:8px;color:#697a98;font-size:13px}
.agents{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.agent-card{position:relative;height:274px;overflow:hidden;padding:20px 24px;border:1px solid #eaf2fc;border-radius:15px;background:linear-gradient(120deg,#fff,#fcfeff);box-shadow:0 4px 13px #4268910b}.card-art{position:absolute;right:0;top:0;width:34%;height:195px;object-fit:fill;opacity:.95;pointer-events:none}.card-top{position:relative;display:flex;align-items:center;gap:18px}.agent-icon{width:68px;height:67px;border-radius:13px;box-shadow:0 5px 10px #006eff12}.card-top h3{margin:0 0 1px;font-size:23px;font-weight:700}.scope{margin:0;color:var(--muted);font-size:17px}.description{position:relative;min-height:54px;max-width:89%;margin:12px 0 15px;color:var(--muted);font-size:17px;line-height:1.6}.tags{position:relative;display:flex;gap:12px}.tags span{padding:5px 16px;border-radius:11px;background:#e4f0ff;color:#0071ff;font-size:16px;white-space:nowrap}.card-bottom{position:relative;display:flex;align-items:center;gap:24px;margin-top:14px}.enter{display:flex;width:184px;height:44px;align-items:center;justify-content:center;border-radius:12px;background:linear-gradient(110deg,#0073ff,#359bff);box-shadow:0 4px 8px #0073ff20;color:#fff;text-decoration:none;font-size:18px;font-weight:500}.enter b{margin-left:16px;font-size:24px;font-weight:300}.green .enter{background:linear-gradient(110deg,#00ad87,#14c79d)}.orange .enter{background:linear-gradient(110deg,#ff8900,#ff9b10)}.card-bottom small{color:#697c9d;font-size:13px;white-space:nowrap}
.bottom{display:grid;grid-template-columns:2fr 1fr;gap:12px;margin-top:12px}.panel{min-width:0;padding:12px 18px 14px;border:1px solid #eaf2fc;border-radius:14px;background:#fff;box-shadow:0 3px 13px #42689108}.panel-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:9px}.panel h2{gap:14px;font-size:19px}.panel h2:before{width:7px;height:21px}.link{padding:0;border:0;background:none;color:#0075ff;cursor:pointer;text-decoration:none;font-size:14px}.table-wrap{overflow:auto;border:1px solid #edf3fb;border-radius:7px}table{width:100%;border-collapse:collapse;color:#3e5781;font-size:14px;text-align:left;white-space:nowrap}th{padding:9px 15px;background:#edf3fa;color:#52678c;font-weight:400}td{padding:8px 15px;border-top:1px solid #e9f0f8}td:first-child{font-weight:500}.prediction-skeleton span{display:block;width:72%;height:13px;border-radius:4px;background:linear-gradient(90deg,#edf3fb 25%,#f8fbff 45%,#edf3fb 65%);background-size:250% 100%;animation:metric-loading 1.25s ease-in-out infinite}.empty-predictions{text-align:center;color:#7180a0}.status{padding:2px 9px;border-radius:20px;background:#e9faf4;color:#00aa84;white-space:nowrap}.status:before{display:inline-block;width:10px;height:10px;margin-right:8px;border-radius:50%;background:currentColor;content:""}.status.running{background:#e9f2ff;color:#0075ff}.workflow-panel{display:flex;flex-direction:column;padding-bottom:16px}.workflow-panel .panel-head{position:relative;justify-content:center;margin-top:4px}.workflow-panel .panel-head h2{font-size:21px}.workflow-panel .flow-label{position:absolute;right:0}.flow-label{border-radius:5px;background:#f1f7ff;color:#7990af;padding:4px 8px;font-size:11px}.workflow{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));align-content:center;justify-content:center;flex:1;gap:11px;margin:16px 0 0;padding:0;list-style:none}.workflow li{display:flex;min-width:0;min-height:62px;align-items:center;gap:9px;border:1px solid #e7effb;border-radius:10px;background:linear-gradient(135deg,#f4f9ff,#fff);padding:10px 9px}.workflow b{display:grid;width:27px;height:27px;flex:0 0 27px;place-items:center;border-radius:8px;background:#e0efff;color:#0075ff;font-size:13px}.workflow strong,.workflow small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.workflow strong{font-size:14px}.workflow small{margin-top:4px;color:#7990ac;font-size:11px}.quick-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 12px}.quick{display:flex;height:56px;align-items:center;justify-content:center;gap:18px;border:1px solid #e8f0fa;border-radius:10px;background:linear-gradient(100deg,#f9fbfe,#f3f7fc);color:var(--ink);text-decoration:none;font-size:15px;font-weight:500}.quick-icon{width:43px;height:39px;object-fit:contain}.quick-note{display:flex;align-items:center;justify-content:center;gap:14px;margin:13px 0 0;color:#7180a0;font-size:13px;white-space:nowrap}.quick-note:before,.quick-note:after{width:48px;height:1px;background:#dce7f5;content:""}
.hero{height:284px}.hero-background{object-fit:cover;object-position:right center}.hero-copy{padding-top:25px}.hero h1{font-size:44px}.capabilities{position:relative;z-index:2;display:grid;width:67%;grid-template-columns:repeat(4,1fr);gap:13px;margin:19px 0 0 6.65%}.capability{display:flex;min-width:0;align-items:center;gap:10px;padding:12px;border:1px solid #ffffffc9;border-radius:15px;background:linear-gradient(110deg,#ffffffed,#ffffffb5);box-shadow:0 3px 12px #1477ce06}.capability img{width:51px;height:51px;flex:0 0 51px;border-radius:50%;object-fit:cover}.capability strong,.capability small{display:block;white-space:nowrap}.capability strong{font-size:16px}.capability small{margin-top:5px;color:#7789a1;font-size:12px}
@media(max-width:1250px){.home-header{padding:0 26px}.brand{font-size:22px;font-weight:700}.forecast-nav{gap:42px}.forecast-nav a{padding:14px 25px;font-size:17px}.metric{gap:10px;padding:12px}.metric>img{width:56px;height:56px}.metric strong{font-size:30px}.metric h3{font-size:14px}.hero h1{font-size:40px}.hero-copy>p:last-child{font-size:18px}.agent-card{height:285px;padding:18px}.card-top{gap:10px}.card-top h3{font-size:20px}.agent-icon{width:58px;height:58px}.description{max-width:95%;font-size:14px}.tags{gap:5px}.tags span{padding:5px 11px;font-size:13px}.card-bottom{gap:8px;flex-wrap:wrap}.enter{width:155px;font-size:16px}.card-bottom small{font-size:11px}.account-actions{gap:7px}}@media(max-width:850px){.home-header{height:auto;min-height:64px;flex-wrap:wrap;padding:10px 4%;gap:8px}.brand{font-size:16px}.forecast-nav{order:3;width:100%;justify-content:center;gap:4px}.forecast-nav a{padding:12px;font-size:15px}.notice,.user-info,.logout-button{display:none}.hero{height:185px}.hero-copy{padding:25px 0 0 5%}.hero h1{font-size:32px}.hero-copy>p:last-child{font-size:15px}.summary{height:auto;grid-template-columns:repeat(2,1fr)}.metric{min-height:85px}.metric:last-child{grid-column:1/-1}.agents{grid-template-columns:1fr}.agent-card{height:267px}.card-top h3{font-size:23px}.description{font-size:16px}.tags span{font-size:15px}.card-bottom small{font-size:13px}.bottom{grid-template-columns:1fr}.section-head small{font-size:10px}.quick{height:62px}.quick-note{font-size:13px}}

/* 首页保留业务展示感，但将通用控件接入平台视觉规范。 */
.forecast-home{--blue:var(--app-primary);--ink:var(--app-text);--muted:var(--app-text-secondary);background:var(--app-bg)}
.metric strong{display:inline-block;animation:metric-value-in .42s cubic-bezier(.22,1,.36,1) both}
@keyframes metric-value-in{from{opacity:0;transform:translateY(5px) scale(.96)}to{opacity:1;transform:translateY(0) scale(1)}}
.summary,.agent-card,.panel{border-color:var(--app-border);box-shadow:0 8px 24px rgba(15,23,42,.035)}
.metric+.metric:before{background:var(--app-border-soft)}
.section-head h2:before,.panel h2:before{background:var(--app-primary)}
.tags span{border-radius:8px;background:var(--app-primary-soft);color:var(--app-primary-hover)}
.enter{background:var(--app-primary);box-shadow:0 4px 10px rgba(37,99,235,.18)}
.status{background:#ecfdf5;color:var(--app-success)}
.status.running{background:var(--app-primary-soft);color:var(--app-primary)}
.table-wrap{border-color:var(--app-border)}
th{background:#f6f8fc;color:var(--app-text-secondary);font-weight:600}
td{border-top-color:var(--app-border-soft)}
</style>
