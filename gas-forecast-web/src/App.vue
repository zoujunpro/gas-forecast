<template>
  <router-view v-if="isLoginPage" />
  <div v-else class="app-shell">
    <aside :class="['sidebar', { collapsed: isSidebarCollapsed }]">
      <div class="brand" @click="goHome">
        <el-tooltip
          content="天然气预测平台"
          placement="right"
          :disabled="!isSidebarCollapsed"
        >
          <div class="brand-mark">
            <img :src="logoUrl" alt="" />
          </div>
        </el-tooltip>
        <div v-if="!isSidebarCollapsed" class="brand-text">
          <strong>天然气预测平台</strong>
        </div>
      </div>

      <el-menu
        class="side-menu"
        :default-active="activeMenu"
        :collapse="isSidebarCollapsed"
        :collapse-transition="false"
        router
      >
        <SidebarMenu :menus="menus" :collapsed="isSidebarCollapsed" />
      </el-menu>
    </aside>

    <section class="main-area">
      <header class="topbar">
        <div class="topbar-heading">
          <el-tooltip :content="isSidebarCollapsed ? '展开菜单' : '折叠菜单'" placement="bottom">
            <button class="collapse-button" type="button" @click="toggleSidebar">
              <el-icon>
                <Expand v-if="isSidebarCollapsed" />
                <Fold v-else />
              </el-icon>
            </button>
          </el-tooltip>
          <span class="header-slogan">问题导向，事前算赢</span>
        </div>
        <div class="user-actions">
          <el-tooltip content="搜索菜单" placement="bottom">
            <button class="header-action" type="button" aria-label="搜索菜单">
              <el-icon><Search /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="帮助" placement="bottom">
            <button class="header-action" type="button" aria-label="帮助">
              <el-icon><QuestionFilled /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="通知" placement="bottom">
            <button class="header-action" type="button" aria-label="通知">
              <el-badge is-dot>
                <el-icon><Bell /></el-icon>
              </el-badge>
            </button>
          </el-tooltip>

          <el-dropdown trigger="click">
            <button class="user-profile" type="button">
              <el-avatar :size="26" class="user-avatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span class="welcome-text">欢迎您，{{ welcomeName }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="UserFilled">个人中心</el-dropdown-item>
                <el-dropdown-item :icon="Setting">账户设置</el-dropdown-item>
                <el-dropdown-item :icon="SwitchButton" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <button class="logout-action" type="button" @click="logout">
            <el-icon><SwitchButton /></el-icon>
            <span>退出登录</span>
          </button>
        </div>
      </header>

      <PageTabs />

      <main class="content">
        <router-view />
      </main>

      <footer class="footerbar">
        <span>天然气市场需求智能预测平台</span>
        <span>本地开发环境</span>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Expand,
  Fold,
  Bell,
  QuestionFilled,
  Search,
  Setting,
  SwitchButton,
  UserFilled
} from '@element-plus/icons-vue'
import PageTabs from '@/components/PageTabs.vue'
import SidebarMenu from '@/components/SidebarMenu.vue'
import logoUrl from '@/assets/logo.png'
import { clearToken, getProfile, getToken, refreshProfile } from '@/utils/auth'
import type { AuthProfile } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const isSidebarCollapsed = ref(false)
const profile = ref<AuthProfile>(getProfile())

const activeMenu = computed(() => route.path)
const isLoginPage = computed(() => route.path === '/login')
const menus = computed(() => profile.value.menus || [])
const welcomeName = computed(() => profile.value.user?.realName || '管理员')

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

const goHome = () => {
  void router.push('/')
}

const logout = () => {
  clearToken()
  profile.value = {}
  void router.replace('/login')
}

onMounted(() => {
  if (getToken()) {
    void refreshProfile().then((nextProfile) => {
      profile.value = nextProfile
    })
  }
})

watch(
  () => route.fullPath,
  () => {
    profile.value = getProfile()
  }
)
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  font-family: var(--app-font-family);
}

body {
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background: var(--app-bg);
}

button,
input,
textarea,
select {
  font-family: inherit;
}

.el-table {
  --el-table-row-hover-bg-color: #DFF1FF;
}

.el-table__body tr:hover > .el-table__cell,
.el-table__body tr.hover-row > .el-table__cell,
.el-table__body tr.hover-row.current-row > .el-table__cell,
.el-table__body tr.hover-row.el-table__row--striped > .el-table__cell {
  background-color: #DFF1FF !important;
}

.el-table__body tr:hover > .el-table-fixed-column--left,
.el-table__body tr:hover > .el-table-fixed-column--right,
.el-table__body tr.hover-row > .el-table-fixed-column--left,
.el-table__body tr.hover-row > .el-table-fixed-column--right {
  background-color: #DFF1FF !important;
}
</style>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: var(--app-bg);
  color: #172033;
}

.sidebar {
  width: 248px;
  min-height: 100vh;
  background: var(--app-surface);
  border-right: 1px solid var(--app-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.2s ease;
}

.sidebar.collapsed {
  width: 64px;
}

.brand {
  height: 59px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  background: var(--app-primary);
  border-bottom: 1px solid var(--app-primary-dark);
  cursor: pointer;
}

.sidebar.collapsed .brand {
  justify-content: center;
  padding: 0;
}

.brand-mark {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-mark img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
}

.brand-text {
  min-width: 0;
}

.brand-text strong {
  display: block;
  overflow: hidden;
  color: #FFFFFF;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-menu {
  flex: 1;
  width: 100%;
  border-right: none;
  padding: 10px 8px 16px;
  font-family: inherit;
}

.side-menu:not(.el-menu--collapse) {
  width: 100%;
}

.sidebar.collapsed .side-menu {
  width: 64px;
  padding: 10px 0 16px;
}

.sidebar.collapsed .side-menu :deep(.el-menu) {
  width: 64px;
}

.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  height: 42px;
  line-height: 42px;
  border-radius: 8px;
  color: #536071;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
}

.sidebar.collapsed .side-menu :deep(.el-menu-item),
.sidebar.collapsed .side-menu :deep(.el-sub-menu__title) {
  width: 52px;
  height: 42px;
  margin: 0 auto;
  justify-content: center;
  padding: 0 !important;
}

.sidebar.collapsed .side-menu :deep(.el-icon) {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0 !important;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: #EAF6FD;
  color: #0284C7;
  font-weight: 600;
}

.main-area {
  min-width: 0;
  min-height: 100vh;
  display: flex;
  flex: 1;
  flex-direction: column;
}

.topbar {
  height: 59px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 24px;
  background: #1890FF;
  border-bottom: 1px solid #0F7FE5;
}

.topbar-heading {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
}

.collapse-button {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.1);
  color: #FFFFFF;
  cursor: pointer;
}

.collapse-button:hover {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.42);
}

.header-slogan {
  color: #FFFFFF;
  font-size: 17px;
  line-height: 1;
  white-space: nowrap;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  height: 100%;
}

.header-action,
.user-profile,
.logout-action {
  height: 100%;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border: none;
  background: transparent;
  color: #FFFFFF;
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.header-action {
  width: 46px;
  justify-content: center;
  padding: 0;
}

.header-action:hover,
.user-profile:hover,
.logout-action:hover {
  background: rgba(255, 255, 255, 0.24);
}

.header-action :deep(.el-badge__content.is-fixed.is-dot) {
  right: 3px;
  top: 12px;
}

.user-avatar {
  color: #1890FF;
  background: rgba(255, 255, 255, 0.88);
}

.welcome-text {
  white-space: nowrap;
}

.logout-action {
  margin-left: 4px;
}

.content {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 14px;
}

.footerbar {
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #FFFFFF;
  border-top: 1px solid #E6EAF0;
  color: #8A95A6;
  font-size: 12px;
}

@media (max-width: 980px) {
  .sidebar {
    width: 220px;
  }

  .user-actions {
    display: none;
  }
}
</style>
