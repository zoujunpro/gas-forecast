<template>
  <el-config-provider :locale="zhCn">
    <router-view v-if="isLoginPage" />
    <div v-else class="app-shell">
      <aside :class="['sidebar', { collapsed: isSidebarCollapsed }]">
        <div class="brand" @click="goHome">
          <el-tooltip content="天然气预测平台" placement="right" :disabled="!isSidebarCollapsed">
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
            <el-tooltip content="搜索菜单功能建设中" placement="bottom">
              <button class="header-action" type="button" aria-label="搜索菜单功能建设中" disabled>
                <el-icon><Search /></el-icon>
              </button>
            </el-tooltip>
            <el-tooltip content="帮助功能建设中" placement="bottom">
              <button class="header-action" type="button" aria-label="帮助功能建设中" disabled>
                <el-icon><QuestionFilled /></el-icon>
              </button>
            </el-tooltip>
            <el-tooltip content="通知功能建设中" placement="bottom">
              <button class="header-action" type="button" aria-label="通知功能建设中" disabled>
                <el-badge>
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
                  <el-dropdown-item :icon="UserFilled" disabled>个人中心（建设中）</el-dropdown-item>
                  <el-dropdown-item :icon="Setting" disabled>账户设置（建设中）</el-dropdown-item>
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
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useRoute, useRouter } from 'vue-router'
import { Expand, Fold, Bell, QuestionFilled, Search, Setting, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import PageTabs from '@/components/PageTabs.vue'
import SidebarMenu from '@/components/SidebarMenu.vue'
import logoUrl from '@/assets/logo.png'
import { clearToken, getProfile, getToken, PROFILE_UPDATED_EVENT, refreshProfile } from '@/utils/auth'
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

const handleAuthExpired = () => {
  profile.value = {}
  if (route.path !== '/login') {
    void router.replace({ path: '/login', query: { redirect: route.fullPath } })
  }
}

const handleProfileUpdated = () => {
  profile.value = getProfile()
}

onMounted(() => {
  window.addEventListener('auth:expired', handleAuthExpired)
  window.addEventListener(PROFILE_UPDATED_EVENT, handleProfileUpdated)
  if (getToken()) {
    void refreshProfile().then((nextProfile) => {
      profile.value = nextProfile
    })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('auth:expired', handleAuthExpired)
  window.removeEventListener(PROFILE_UPDATED_EVENT, handleProfileUpdated)
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
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-body);
}

body {
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background: var(--app-bg);
  color: var(--app-text);
  text-rendering: optimizeLegibility;
}

button,
input,
textarea,
select {
  font-family: inherit;
}

.el-button,
.el-input,
.el-select,
.el-form,
.el-table,
.el-pagination,
.el-dialog,
.el-drawer,
.el-message-box,
.el-dropdown,
.el-menu {
  font-family: var(--app-font-family);
}

.el-button,
.el-input__inner,
.el-select__selected-item,
.el-select__placeholder,
.el-form-item__label,
.el-table .cell,
.el-pagination button,
.el-pagination .number {
  font-size: var(--app-font-size-body);
}

.el-button,
.el-form-item__label,
.el-table th.el-table__cell > .cell {
  font-weight: var(--app-font-weight-semibold);
}

.el-table td.el-table__cell {
  color: var(--app-text-secondary);
  font-weight: var(--app-font-weight-medium);
}

.el-table th.el-table__cell {
  color: var(--app-text);
}

.el-input__inner,
.el-select__selected-item,
.el-table .cell,
.el-statistic__number,
.metric-value {
  font-variant-numeric: tabular-nums;
}

.el-table {
  --el-table-row-hover-bg-color: #e8f3ff;
}

.el-table__body tr:hover > .el-table__cell,
.el-table__body tr.hover-row > .el-table__cell,
.el-table__body tr.hover-row.current-row > .el-table__cell,
.el-table__body tr.hover-row.el-table__row--striped > .el-table__cell {
  background-color: #e8f3ff !important;
}

.el-table__body tr:hover > .el-table-fixed-column--left,
.el-table__body tr:hover > .el-table-fixed-column--right,
.el-table__body tr.hover-row > .el-table-fixed-column--left,
.el-table__body tr.hover-row > .el-table-fixed-column--right {
  background-color: #e8f3ff !important;
}
</style>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
  background: var(--app-bg);
  color: var(--app-text);
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
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
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
  color: var(--app-text-secondary);
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
  background: var(--app-primary-soft);
  color: var(--app-primary-hover);
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
  background: var(--app-primary);
  border-bottom: 1px solid var(--app-primary-dark);
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
  color: #ffffff;
  cursor: pointer;
}

.collapse-button:hover {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.42);
}

.header-slogan {
  color: #ffffff;
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
  color: #ffffff;
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

.header-action:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.header-action:disabled:hover {
  background: transparent;
}

.header-action :deep(.el-badge__content.is-fixed.is-dot) {
  right: 3px;
  top: 12px;
}

.user-avatar {
  color: var(--app-primary);
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
  background: var(--app-surface);
  border-top: 1px solid var(--app-border);
  color: var(--app-text-muted);
  font-size: 13px;
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
