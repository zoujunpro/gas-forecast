<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowUpRight, ChevronRight, Menu, X, Database, BrainCircuit, Network, BookOpenCheck, FlaskConical, Factory, GraduationCap, Cpu, Play, Mail, Phone } from 'lucide-vue-next'

const route = useRoute()
const menuOpen = ref(false)
const scrolled = ref(false)
const onScroll = () => { scrolled.value = window.scrollY > 24 }
onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onUnmounted(() => window.removeEventListener('scroll', onScroll))

const nav = [
  { label: '首页', to: '/' }, { label: '新纪平台', to: '/platform' }, { label: '科研创新', to: '/research' },
  { label: '产品中心', to: '/products' }, { label: '场景应用', to: '/applications' },
  { label: '生态合作', to: '/ecosystem' }, { label: '新闻洞察', to: '/news' }, { label: '关于我们', to: '/about' }
]
const current = computed(() => nav.find(item => item.to === route.path)?.label || '首页')

const research = [
  { icon: Database, no: '01', title: '异构机器人多源数据生成', desc: '统一多模态操作数据，研究跨本体数据对齐、域自适应迁移与工业知识注入。' },
  { icon: BrainCircuit, no: '02', title: 'VLA 世界模型与技能库', desc: '贯通感知、状态预演和动作生成，构建可组合、可迁移、可扩展的原子技能体系。' },
  { icon: Network, no: '03', title: '多智能体协同与调度', desc: '面向人机混合与异构机器人集群，研究任务分配、无冲突规划和在线自愈。' },
  { icon: BookOpenCheck, no: '04', title: '知识基座与策略自学习', desc: '沉淀通用技能策略池，支持长程任务规划、执行评估、反馈优化与持续进化。' }
]
const applications = [
  { icon: Factory, title: '工业能源', desc: '面向电力、能源和制造场景的移动巡检、精细操作与人机协同。' },
  { icon: FlaskConical, title: '科学实验室', desc: '服务 AI for Science，支持实验操作、数据采集和无人化科研探索。' },
  { icon: GraduationCap, title: '科研教育', desc: '覆盖课程、仿真、真机实验、竞赛项目与科研课题的教学科研平台。' }
]
const pages = {
  platform: { eyebrow: 'XINJI EMBODIED AI PLATFORM', title: '新纪具身智能生态平台', intro: '以机器人为载体，以数据和模型为核心，连接技术研发、产品验证、科研协同与产业落地。' },
  research: { eyebrow: 'RESEARCH & INNOVATION', title: '科研创新', intro: '围绕数据、模型、协作与知识四层架构，研究面向工业制造的全流程具身智能。' },
  products: { eyebrow: 'ROBOT PRODUCTS', title: '产品中心', intro: '以大圣 DASHENG 为首款产品，构建开放、可扩展的具身机器人产品体系。' },
  applications: { eyebrow: 'REAL-WORLD APPLICATIONS', title: '场景应用', intro: '让具身智能围绕真实任务持续验证，在科研与产业中形成可复制的解决方案。' },
  ecosystem: { eyebrow: 'OPEN ECOSYSTEM', title: '生态合作', intro: '连接高校、研究院所、开发者与产业伙伴，共建开放协同的具身智能生态。' },
  news: { eyebrow: 'NEWS & INSIGHTS', title: '新闻洞察', intro: '关注具身智能政策、技术与产业进展，记录新纪生态的持续成长。' },
  about: { eyebrow: 'ABOUT BEIJING EI', title: '关于我们', intro: '北京具身智能科技有限公司，面向真实世界开展具身智能技术研发与产业实践。' }
}
</script>

<template>
  <div class="site">
    <header :class="['header', { solid: scrolled || route.path !== '/' }]">
      <router-link class="brand" to="/" @click="menuOpen=false"><span class="brand-symbol"><i></i><i></i><i></i></span><span><b>北京具身智能</b><small>BEIJING EMBODIED INTELLIGENCE</small></span></router-link>
      <nav :class="{ open: menuOpen }"><router-link v-for="item in nav" :key="item.to" :to="item.to" @click="menuOpen=false">{{ item.label }}</router-link></nav>
      <router-link class="consult" to="/about">联系合作 <ArrowUpRight :size="16" /></router-link>
      <button class="menu" @click="menuOpen=!menuOpen" aria-label="切换导航"><X v-if="menuOpen"/><Menu v-else/></button>
    </header>

    <main v-if="route.path === '/'">
      <section class="hero">
        <video autoplay muted loop playsinline poster="/images/hero-poster.jpg"><source src="/videos/hero-demo.mp4" type="video/mp4" /></video><div class="hero-shade"></div>
        <div class="hero-content"><p class="eyebrow light">XINJI EMBODIED INTELLIGENCE ECOSYSTEM</p><h1>让智能机器<br/><em>走进真实世界</em></h1><p class="hero-lead">新纪具身智能生态平台</p><p class="hero-desc">贯通机器人本体、数据、模型与场景，构建面向科研创新和产业应用的开放生态。</p><div class="actions"><router-link class="btn primary" to="/platform">探索新纪平台 <ArrowUpRight :size="17" /></router-link><router-link class="btn glass" to="/research"><Play :size="14" fill="currentColor"/> 了解科研体系</router-link></div></div>
        <div class="hero-strip"><div><small>PLATFORM</small><b>新纪生态平台</b></div><div><small>RESEARCH</small><b>四大科研方向</b></div><div><small>PRODUCT</small><b>大圣开源机器人</b></div><div><small>APPLICATION</small><b>真实场景落地</b></div></div>
      </section>

      <section class="intro section"><div class="section-label">01 / BUSINESS ARCHITECTURE</div><div><h2>以新纪平台连接具身智能的<br/><em>研究、产品与应用</em></h2><p>北京具身智能科技有限公司建设“新纪具身智能生态平台”。科研体系持续形成数据、模型和技能能力，机器人产品承载技术验证，真实场景反馈又推动平台进化。</p></div></section>

      <section class="platform section dark"><div class="section-head"><div><p class="eyebrow">XINJI PLATFORM</p><h2>具身智能全链路能力底座</h2></div><router-link to="/platform">了解平台架构 <ArrowUpRight :size="17"/></router-link></div><div class="platform-flow"><div><span>01</span><b>数据生成</b><small>真实数据 / 仿真数据 / 跨本体迁移</small></div><ChevronRight/><div><span>02</span><b>模型训练</b><small>VLA / 世界模型 / 技能库</small></div><ChevronRight/><div><span>03</span><b>部署验证</b><small>真机测试 / 评估反馈 / 持续优化</small></div><ChevronRight/><div><span>04</span><b>场景应用</b><small>科研教育 / 工业能源 / 科学实验</small></div></div></section>

      <section class="research section"><div class="section-head"><div><p class="eyebrow">RESEARCH & INNOVATION</p><h2>四大科研方向</h2></div><p>从数据层、模型层到协作层与交互层，形成完整的具身智能研究架构。</p></div><div class="research-grid"><article v-for="item in research" :key="item.no"><span>{{ item.no }}</span><component :is="item.icon" :size="31"/><h3>{{ item.title }}</h3><p>{{ item.desc }}</p><router-link to="/research">研究详情 <ArrowUpRight :size="15"/></router-link></article></div></section>

      <section class="product-feature"><div class="product-image"><img src="/images/dasheng-brochure-1.png" alt="大圣 DASHENG 轮式人形机器人"/></div><div class="product-copy"><p class="eyebrow">ROBOT PRODUCT 01</p><h2>大圣 <em>DASHENG</em></h2><h3>EI-Stack 轮式人形机器人</h3><p>面向高校、科研团队、开发者与行业客户，覆盖仿真验证、数据采集、模型训练与部署推理。大圣是新纪生态平台的首款机器人产品，也是技术验证与能力开放的重要载体。</p><div class="stats"><div><b>23</b><span>整机自由度</span></div><div><b>2×7</b><span>双臂自由度</span></div><div><b>67 TOPS</b><span>基础算力</span></div><div><b>4h</b><span>续航时间</span></div></div><router-link class="btn ink" to="/products">查看产品详情 <ArrowUpRight :size="17"/></router-link></div></section>

      <section class="applications section"><div class="section-head"><div><p class="eyebrow">APPLICATIONS</p><h2>面向真实任务的场景体系</h2></div><router-link to="/applications">全部场景 <ArrowUpRight :size="17"/></router-link></div><div class="application-grid"><article v-for="item in applications" :key="item.title"><component :is="item.icon" :size="28"/><div><h3>{{ item.title }}</h3><p>{{ item.desc }}</p></div></article><img src="/images/applications.jpg" alt="大圣机器人应用场景示意"/></div></section>

      <section class="ecosystem section dark"><div><p class="eyebrow">OPEN ECOSYSTEM</p><h2>共建具身智能生态共同体</h2><p>以技术研发和机器人产品为基础，通过大会、竞赛、标准、开源社区与科普教育连接行业资源。</p><router-link class="btn pale" to="/ecosystem">加入生态合作 <ArrowUpRight :size="17"/></router-link></div><div class="orbit"><div class="core"><b>新纪</b><small>具身生态平台</small></div><span class="o1">机器人</span><span class="o2">数据</span><span class="o3">开源社区</span><span class="o4">大会竞赛</span><span class="o5">标准</span><span class="o6">科普教育</span></div></section>

      <section class="contact section"><p class="eyebrow">CO-CREATE THE FUTURE</p><h2>与我们一起，让具身智能<br/>成为真实生产力</h2><router-link class="circle-link" to="/about">开启合作 <ArrowUpRight/></router-link></section>
    </main>

    <main v-else class="inner-page">
      <section class="page-hero"><p class="eyebrow light">{{ pages[route.name]?.eyebrow }}</p><h1>{{ pages[route.name]?.title || current }}</h1><p>{{ pages[route.name]?.intro }}</p></section>

      <section v-if="route.name === 'research'" class="section research-page"><div class="research-intro"><div><p class="section-label">RESEARCH ARCHITECTURE</p><h2>面向工业制造的<br/>全流程具身智能</h2></div><p>科研模块不是产品参数的补充，而是新纪平台持续进化的技术源头。四个方向分别解决高质量数据不足、模型泛化有限、群体协同低效和知识难以积累的问题。</p></div><div class="research-grid detailed"><article v-for="item in research" :key="item.no"><span>{{ item.no }}</span><component :is="item.icon" :size="34"/><h3>{{ item.title }}</h3><p>{{ item.desc }}</p><ul><li v-if="item.no==='01'">多模态数据采集与融合</li><li v-if="item.no==='01'">跨本体对齐与迁移</li><li v-if="item.no==='02'">世界模型感知增强</li><li v-if="item.no==='02'">原子技能解耦与重组</li><li v-if="item.no==='03'">异构任务动态分配</li><li v-if="item.no==='03'">扰动下在线重调度</li><li v-if="item.no==='04'">通用技能策略池</li><li v-if="item.no==='04'">评估反馈闭环优化</li></ul></article></div><div class="training-ground"><img src="/images/research-lab.jpg" alt="大圣机器人科研实验室"/><div><p class="eyebrow">XINJI TRAINING GROUND</p><h2>新纪具身训练场</h2><p>围绕虚实环境数据采集、仿真算法测试、真实设备验证与模型反馈迭代，构建一次采集、跨本体复用的训练验证体系。</p><div class="chips"><span>4D 场景数字化</span><span>Sim-to-Real</span><span>模型闭环训练</span><span>真机部署反馈</span></div></div></div><div class="collaboration"><p class="eyebrow">OPEN RESEARCH</p><h2>开放科研合作</h2><p>面向高校、科研机构和技术团队，开展联合课题、数据与模型研究、算法验证、论文专利和成果转化合作。</p><router-link class="btn primary" to="/about">发起科研合作 <ArrowUpRight :size="17"/></router-link></div></section>

      <section v-else-if="route.name === 'products'" class="section product-page"><div class="product-feature"><div class="product-image"><img src="/images/dasheng-brochure-1.png" alt="大圣机器人产品图"/></div><div class="product-copy"><p class="eyebrow">DASHENG / PRODUCT 01</p><h2>大圣开源平台</h2><p>全球首款 EI-Stack 轮式人形机器人，提供标准版与个性化定制服务。</p><div class="stats"><div><b>23</b><span>整机自由度</span></div><div><b>50kg</b><span>整机重量</span></div><div><b>1m/s</b><span>最大速度</span></div><div><b>ROS2</b><span>Humble</span></div></div></div></div><img class="spec-image" src="/images/dasheng-brochure-3.png" alt="大圣机器人性能参数"/></section>

      <section v-else-if="route.name === 'platform'" class="section generic"><div class="section-head"><div><p class="eyebrow">PLATFORM ARCHITECTURE</p><h2>平台内容架构</h2></div><p>新纪平台统一承载技术、产品、科研、场景与生态内容。</p></div><div class="pillar-grid"><article><Cpu/><h3>技术底座</h3><p>数据、模型、技能与多智能体协作能力。</p></article><article><FlaskConical/><h3>科研创新</h3><p>四大研究方向与具身训练场。</p></article><article><BrainCircuit/><h3>产品载体</h3><p>大圣机器人及后续系列产品。</p></article><article><Network/><h3>开放生态</h3><p>产学研合作、标准、赛事与社区。</p></article></div></section>

      <section v-else-if="route.name === 'applications'" class="section generic"><div class="application-grid standalone"><article v-for="item in applications" :key="item.title"><component :is="item.icon"/><div><h3>{{ item.title }}</h3><p>{{ item.desc }}</p></div></article><img src="/images/applications.jpg" alt="机器人应用场景示意"/></div></section>

      <section v-else-if="route.name === 'ecosystem'" class="section generic"><div class="ecosystem-board"><h2>“新纪”具身智能生态</h2><p>建议后续展开大会与竞赛、开源社区、标准工作、科普教育、委员单位和产业伙伴六个独立栏目。</p><div class="chips"><span>具身智能大会</span><span>算法挑战赛</span><span>开源社区</span><span>系列标准</span><span>科普教育</span><span>产业合作</span></div></div></section>

      <section v-else-if="route.name === 'news'" class="section generic"><div class="news-list"><article><time>行业政策 · 2025.02.28</time><h3>北京发布具身智能科技创新与产业培育行动计划</h3><p>重点布局关键技术、创新平台、场景开放与产业生态。</p></article><article><time>产品动态 · 待更新</time><h3>大圣开源平台产品动态</h3><p>这里将用于发布产品升级、版本迭代和交付进展。</p></article><article><time>科研进展 · 待更新</time><h3>科研成果与合作项目</h3><p>这里将用于发布课题、论文、专利和联合实验室进展。</p></article></div></section>

      <section v-else class="section about-page"><div><p class="eyebrow">COMPANY</p><h2>北京具身智能科技有限公司</h2><p>公司成立于 2020 年，专注具身智能技术研发、机器人产品和产业实践。官网建议将团队履历、科研合作、荣誉资质与真实项目分开呈现，并在资料确认后逐步补充。</p></div><div class="contact-card"><h3>合作咨询</h3><p><Phone :size="17"/> 188 1062 8201</p><p><Mail :size="17"/> tcei00@163.com</p><span>产品咨询 · 科研合作 · 产业落地 · 生态共建</span></div></section>
    </main>

    <footer><div class="brand"><span class="brand-symbol"><i></i><i></i><i></i></span><span><b>北京具身智能</b><small>BEIJING EMBODIED INTELLIGENCE</small></span></div><p>新纪具身智能生态平台 · 大圣机器人</p><div><router-link to="/research">科研创新</router-link><router-link to="/products">产品中心</router-link><router-link to="/about">联系我们</router-link><span>© 2026 北京具身智能科技有限公司</span></div></footer>
  </div>
</template>
