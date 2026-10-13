<template>
  <main class="portal-page">
    <div class="portal-overlay"></div>
    <header class="portal-header">
      <div class="portal-brand"><img :src="logoUrl" alt="" /><span>天然气市场需求智能预测平台</span></div>
      <div class="portal-user">
        <span>欢迎您，{{ userName }}</span
        ><button type="button" @click="logout">退出登录</button>
      </div>
    </header>
    <section class="portal-content">
      <p class="eyebrow">NATURAL GAS INTELLIGENCE</p>
      <h1>请选择您的工作入口</h1>
      <p class="subtitle">面向业务人员的智能问答，与面向管理人员的配置后台相互独立</p>
      <div class="entrance-grid">
        <RouterLink class="entrance-card ai-card" to="/ai-assistant">
          <div class="card-glow"></div>
          <div class="entrance-icon">
            <el-icon><MagicStick /></el-icon>
          </div>
          <div class="entrance-copy">
            <span class="entrance-tag">业务入口</span>
            <h2>AI 智能问答中心</h2>
            <p>集中使用智能问答和预测智能体，查询结果、解读趋势并完成预测业务。</p>
            <ul>
              <li>AI 智能问答</li>
              <li>预测智能体中心</li>
              <li>支持上下文连续追问</li>
            </ul>
            <strong
              >进入问答中心 <el-icon><Right /></el-icon
            ></strong>
          </div>
        </RouterLink>
        <RouterLink class="entrance-card admin-card" :to="managementPath">
          <div class="card-glow"></div>
          <div class="entrance-icon">
            <el-icon><Setting /></el-icon>
          </div>
          <div class="entrance-copy">
            <span class="entrance-tag">管理入口</span>
            <h2>后台管理</h2>
            <p>维护基础数据、模型配置、训练任务、预测配置以及系统权限。</p>
            <ul>
              <li>模型训练与预测管理</li>
              <li>数据和特征维护</li>
              <li>用户、角色与菜单管理</li>
            </ul>
            <strong
              >进入后台管理 <el-icon><Right /></el-icon
            ></strong>
          </div>
        </RouterLink>
      </div>
    </section>
    <footer>问题导向，事前算赢 · 数据驱动能源决策</footer>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, Right, Setting } from '@element-plus/icons-vue'
import logoUrl from '@/assets/logo.png'
import { clearToken, collectManagementMenuPaths, getProfile } from '@/utils/auth'
const router = useRouter()
const userName = computed(() => getProfile().user?.realName || '管理员')
const managementPath = computed(() => {
  const paths = collectManagementMenuPaths(getProfile().menus || [])
  return paths.find((path) => path === '/model/list') || paths.find((path) => path.includes('/')) || '/'
})
const logout = () => {
  clearToken()
  void router.replace('/login')
}
</script>

<style scoped>
.portal-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #ffffff 0%, #f5f8fc 55%, #eef6fb 100%);
  color: #17233d;
}
.portal-page:before {
  content: '';
  position: absolute;
  inset: 0;
  background: url('@/assets/home-hero-lng-v2.webp') center/cover no-repeat;
  opacity: 0.055;
}
.portal-overlay {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 50% 38%, rgba(58, 135, 238, 0.1), transparent 42%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.18), rgba(238, 246, 252, 0.5));
}
.portal-header {
  position: relative;
  z-index: 1;
  height: 72px;
  padding: 0 5vw;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e5ebf3;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(12px);
}
.portal-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 17px;
  font-weight: 700;
}
.portal-brand img {
  width: 35px;
  height: 35px;
  object-fit: contain;
}
.portal-user {
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 13px;
  color: #66758c;
}
.portal-user button {
  border: 1px solid #d8e0eb;
  border-radius: 7px;
  padding: 7px 13px;
  background: #fff;
  color: #34445e;
  cursor: pointer;
}
.portal-content {
  position: relative;
  z-index: 1;
  max-width: 1120px;
  margin: auto;
  padding: 9vh 28px 80px;
  text-align: center;
}
.eyebrow {
  font-size: 12px;
  letter-spacing: 0.28em;
  color: #4285d4;
}
.portal-content h1 {
  margin: 14px 0 10px;
  font-size: clamp(34px, 4vw, 52px);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #17233d;
}
.subtitle {
  color: #6c7b91;
  font-size: 16px;
}
.entrance-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 28px;
  margin-top: 54px;
  text-align: left;
}
.entrance-card {
  position: relative;
  min-height: 390px;
  overflow: hidden;
  padding: 38px;
  border: 1px solid #dfe6ef;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 16px 45px rgba(30, 64, 112, 0.09);
  backdrop-filter: blur(15px);
  color: #17233d;
  text-align: left;
  cursor: pointer;
  transition: 0.25s;
  text-decoration: none;
}
.entrance-card:hover {
  transform: translateY(-7px);
  border-color: #9fc2ee;
  box-shadow: 0 24px 58px rgba(30, 94, 170, 0.16);
}
.card-glow {
  position: absolute;
  width: 210px;
  height: 210px;
  right: -80px;
  top: -90px;
  border-radius: 50%;
  filter: blur(4px);
}
.ai-card .card-glow {
  background: rgba(107, 92, 255, 0.12);
}
.admin-card .card-glow {
  background: rgba(39, 196, 172, 0.12);
}
.entrance-icon {
  position: relative;
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  font-size: 28px;
}
.ai-card .entrance-icon {
  background: linear-gradient(135deg, #7868ff, #4e7df2);
}
.admin-card .entrance-icon {
  background: linear-gradient(135deg, #20b99d, #1686b5);
}
.entrance-copy {
  position: relative;
}
.entrance-tag {
  display: inline-block;
  margin-top: 25px;
  padding: 4px 10px;
  border-radius: 20px;
  background: #eef4fb;
  font-size: 11px;
  color: #5c7190;
}
.entrance-copy h2 {
  margin: 12px 0 10px;
  font-size: 27px;
}
.entrance-copy p {
  min-height: 52px;
  color: #66758c;
  line-height: 1.7;
}
.entrance-copy ul {
  display: grid;
  gap: 9px;
  margin: 20px 0 27px;
  padding: 0;
  list-style: none;
  color: #44536a;
  font-size: 13px;
}
.entrance-copy li:before {
  content: '✓';
  margin-right: 9px;
  color: #75e4c7;
}
.entrance-copy strong {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #286bb8;
}
.portal-page footer {
  position: absolute;
  z-index: 1;
  bottom: 20px;
  width: 100%;
  text-align: center;
  color: #8b98aa;
  font-size: 12px;
}
@media (max-width: 760px) {
  .portal-header {
    padding: 0 20px;
  }
  .portal-brand span {
    font-size: 14px;
  }
  .portal-user span {
    display: none;
  }
  .portal-content {
    padding-top: 45px;
  }
  .entrance-grid {
    grid-template-columns: 1fr;
    margin-top: 30px;
  }
  .entrance-card {
    min-height: auto;
  }
  .portal-page footer {
    position: relative;
    padding-bottom: 18px;
  }
}
</style>
