<template>
  <el-config-provider :locale="zhCn">
    <router-view v-if="isLoginPage || isStandalonePage" :key="route.fullPath" />
    <div v-else-if="isForecastPage" class="forecast-shell">
      <header class="forecast-header">
        <button class="forecast-brand" type="button" @click="goHome">
          <span class="forecast-logo">
            <svg viewBox="0 0 32 32" aria-hidden="true">
              <path d="M16 3S5 10 5 18.5a11 11 0 0 0 22 0C27 10 16 3 16 3Z" fill="currentColor" />
              <path d="M16 23V11m0 0-5 5m5-5 5 5" fill="none" stroke="#fff" stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" />
            </svg>
          </span>
          <strong>天然气市场需求智能预测平台</strong>
        </button>

        <nav class="forecast-navigation" aria-label="预测工作台导航">
          <RouterLink
            v-for="menu in topMenus"
            :key="menu.id"
            :to="topMenuTarget(menu)"
            :class="{ active: activeTopMenu?.id === menu.id }"
          >{{ topMenuName(menu) }}</RouterLink>
        </nav>

        <div class="forecast-account">
          <span>{{ welcomeName }}</span>
          <button type="button" @click="logout">退出</button>
        </div>
      </header>
      <div class="forecast-body">
        <main class="forecast-content"><router-view :key="route.fullPath" /></main>
      </div>
    </div>
    <div v-else class="app-shell">
      <header class="topbar">
        <button class="forecast-brand" type="button" @click="goHome">
          <span class="forecast-logo">
            <svg viewBox="0 0 32 32" aria-hidden="true">
              <path d="M16 3S5 10 5 18.5a11 11 0 0 0 22 0C27 10 16 3 16 3Z" fill="currentColor" />
              <path d="M16 23V11m0 0-5 5m5-5 5 5" fill="none" stroke="#fff" stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" />
            </svg>
          </span>
          <strong>天然气市场需求智能预测平台</strong>
        </button>
        <nav class="workspace-switch" aria-label="中心切换">
          <RouterLink
            v-for="menu in topMenus"
            :key="menu.id"
            :to="topMenuTarget(menu)"
            :class="{ active: activeTopMenu?.id === menu.id }"
          >{{ topMenuName(menu) }}</RouterLink>
        </nav>
        <div class="forecast-account">
          <span>{{ welcomeName }}</span>
          <button type="button" @click="logout">退出</button>
        </div>
      </header>

      <div class="app-body">
        <aside :class="['sidebar', { collapsed: isSidebarCollapsed }]">
          <div class="sidebar-heading">
            <el-tooltip :content="isSidebarCollapsed ? '展开菜单' : '折叠菜单'" placement="bottom">
              <button class="collapse-button" type="button" @click="toggleSidebar">
                <el-icon>
                  <Expand v-if="isSidebarCollapsed" />
                  <Fold v-else />
                </el-icon>
              </button>
            </el-tooltip>
            <span v-if="!isSidebarCollapsed">{{ activeTopMenu?.name || '功能菜单' }}</span>
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

          <PageTabs v-if="!isImmersivePage" />

          <main :class="['content', { immersive: isImmersivePage }]">
            <router-view :key="route.fullPath" />
          </main>

          <footer v-if="!isImmersivePage" class="footerbar">
            <span>天然气市场需求智能预测平台</span>
            <span>本地开发环境</span>
          </footer>
        </section>
      </div>
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useRoute, useRouter } from 'vue-router'
import { Expand, Fold } from '@element-plus/icons-vue'
import PageTabs from '@/components/PageTabs.vue'
import SidebarMenu from '@/components/SidebarMenu.vue'
import {
  clearToken,
  getProfile,
  getToken,
  PROFILE_UPDATED_EVENT,
  refreshProfile
} from '@/utils/auth'
import type { AuthMenu, AuthProfile } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const isSidebarCollapsed = ref(false)
const profile = ref<AuthProfile>(getProfile())

const activeMenu = computed(() => route.path)
const isLoginPage = computed(() => route.path === '/login')
const isStandalonePage = computed(() => Boolean(route.meta.standalone))
const isImmersivePage = computed(() => Boolean(route.meta.immersive))
const isForecastPage = computed(() => route.path.startsWith('/agent/'))
const isAiCenterPage = computed(
  () => route.path === '/ai-assistant' || route.path === '/agent' || route.path.startsWith('/agent/')
)
const topMenus = computed(() =>
  (profile.value?.menus || [])
    .filter((menu) => !menu.hidden && menu.path !== '/' && Boolean(menu.children?.some((child) => !child.hidden)))
    .sort((left, right) => (left.sortNo || 0) - (right.sortNo || 0))
)

const menuContainsPath = (menu: AuthMenu, path: string): boolean =>
  menu.path === path || Boolean(menu.children?.some((child) => menuContainsPath(child, path)))

const isAgentTopMenu = (menu: AuthMenu) =>
  menu.name === '智能体中心' ||
  menu.path === '/agent' ||
  Boolean(menu.children?.some((child) => child.path?.startsWith('/agent/')))

const activeTopMenu = computed(() => {
  if (isAiCenterPage.value) return topMenus.value.find(isAgentTopMenu)
  return topMenus.value.find((menu) => menuContainsPath(menu, route.path))
})

const menus = computed(() => (activeTopMenu.value?.children || []).filter((menu) => !menu.hidden))
const welcomeName = computed(() => profile.value?.user?.realName || '管理员')

const firstMenuPath = (menu: AuthMenu): string => {
  if (menu.component && menu.path) return menu.path
  for (const child of menu.children || []) {
    const path = firstMenuPath(child)
    if (path) return path
  }
  return ''
}

const topMenuTarget = (menu: AuthMenu) => isAgentTopMenu(menu) ? '/' : firstMenuPath(menu) || '/'
const topMenuName = (menu: AuthMenu) => menu.name === '后台数据管理' ? '数据管理' : menu.name

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
.forecast-shell {
  min-height: 100vh;
  background: #f7f9fc;
  color: #0f172a;
}

.forecast-header {
  position: sticky;
  top: 0;
  z-index: 1000;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.85);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(16px);
}

.forecast-brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #172033;
  cursor: pointer;
}

.forecast-brand strong {
  font-size: 14px;
  font-weight: 650;
  white-space: nowrap;
}

.forecast-logo {
  width: 32px;
  height: 32px;
  color: #0ea5e9;
}

.forecast-logo svg {
  width: 100%;
  height: 100%;
}

.forecast-navigation {
  position: absolute;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 34px;
  padding: 9px 18px;
  transform: translateX(-50%);
}

.forecast-navigation a {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 9px 16px;
  border-radius: 9px;
  color: #64748b;
  font-size: 15px;
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.2s;
}

.forecast-navigation a:hover,
.forecast-navigation a:not(.active):hover {
  color: #0284c7;
}

.forecast-navigation a.active {
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
  color: #ffffff;
  font-weight: 650;
  box-shadow: 0 5px 14px -5px rgba(14, 165, 233, 0.55);
}

.forecast-account {
  display: flex;
  align-items: center;
  gap: 16px;
  color: #64748b;
  font-size: 12px;
}

.forecast-account button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.forecast-account button:hover {
  color: #0284c7;
}

.forecast-content {
  min-width: 0;
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.forecast-body {
  height: calc(100vh - 56px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.agent-workspace-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  flex: 0 0 auto;
  padding: 12px 16px;
  border-bottom: 1px solid #e9eef4;
  background: #f8fafc;
}

.agent-workspace-tabs a {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 11px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  color: #475569;
  text-decoration: none;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}

.agent-workspace-tabs a:hover {
  border-color: #7dd3fc;
}

.agent-workspace-tabs a.active {
  border-color: #38bdf8;
  background: #f0f9ff;
  color: #0284c7;
  box-shadow: 0 3px 12px rgba(14, 165, 233, 0.12);
}

.agent-workspace-tabs a > span:last-child {
  min-width: 0;
}

.agent-workspace-tabs strong,
.agent-workspace-tabs small {
  display: block;
}

.agent-workspace-tabs strong {
  color: inherit;
  font-size: 14px;
  font-weight: 650;
}

.agent-workspace-tabs small {
  margin-top: 3px;
  overflow: hidden;
  color: #94a3b8;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.forecast-sidebar {
  width: 224px;
  flex: 0 0 224px;
  padding: 20px 14px;
  border-right: 1px solid #e9eef4;
  background: #ffffff;
}

.forecast-sidebar-title {
  padding: 0 10px 12px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.forecast-sidebar nav {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.forecast-sidebar a {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 11px 10px;
  border-radius: 10px;
  color: #475569;
  text-decoration: none;
  transition: color 0.2s, background 0.2s;
}

.forecast-sidebar a:hover {
  background: #f8fafc;
  color: #0f172a;
}

.forecast-sidebar a.active {
  background: #f0f9ff;
  color: #0284c7;
  box-shadow: inset 3px 0 0 #0ea5e9;
}

.forecast-sidebar a > span:last-child {
  min-width: 0;
}

.forecast-sidebar strong,
.forecast-sidebar small {
  display: block;
}

.forecast-sidebar strong {
  color: inherit;
  font-size: 14px;
  font-weight: 600;
}

.forecast-sidebar small {
  margin-top: 3px;
  overflow: hidden;
  color: #94a3b8;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-menu-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  border-radius: 9px;
  background: var(--agent-soft);
  color: var(--agent-color);
}

.agent-menu-icon svg {
  width: 18px;
  height: 18px;
}

@media (max-width: 1000px) {
  .forecast-navigation {
    position: static;
    margin-left: auto;
    transform: none;
  }

  .forecast-account span {
    display: none;
  }
}

@media (max-width: 760px) {
  .forecast-header {
    padding: 0 14px;
  }

  .forecast-brand strong,
  .forecast-navigation {
    display: none;
  }

  .agent-workspace-tabs {
    grid-template-columns: repeat(3, minmax(100px, 1fr));
    overflow-x: auto;
  }

  .agent-workspace-tabs a {
    padding: 9px;
  }

  .agent-workspace-tabs a > span:last-child {
    display: none;
  }

  .forecast-sidebar {
    width: 64px;
    flex-basis: 64px;
    padding: 14px 7px;
  }

  .forecast-sidebar-title,
  .forecast-sidebar a > span:last-child {
    display: none;
  }

  .forecast-sidebar a {
    justify-content: center;
    padding: 9px 7px;
  }
}

.app-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  background: var(--app-bg);
  color: var(--app-text);
}

.app-body {
  min-height: 0;
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 248px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
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
  background: #ffffff;
  border-bottom: 1px solid var(--app-border);
  cursor: pointer;
}

.sidebar.collapsed .brand {
  justify-content: center;
  padding: 0;
}

.brand-mark {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #0ea5e9;
}

.brand-mark svg {
  width: 34px;
  height: 34px;
  display: block;
}

.brand-text {
  min-width: 0;
}

.brand-text strong {
  display: block;
  overflow: hidden;
  color: #172033;
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

.sidebar-heading {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 48px;
  padding: 0 12px;
  border-bottom: 1px solid var(--app-border-soft);
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
}

.sidebar.collapsed .sidebar-heading {
  justify-content: center;
  padding: 0;
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
  box-shadow: inset 3px 0 0 var(--app-primary);
}

.main-area {
  min-width: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex: 1;
  flex-direction: column;
}

.topbar {
  position: relative;
  height: 56px;
  flex: 0 0 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid var(--app-border);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.workspace-switch {
  position: absolute;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 34px;
  padding: 4px 18px;
  transform: translateX(-50%);
}

.workspace-switch a {
  padding: 9px 16px;
  border-radius: 9px;
  color: #64748b;
  font-size: 15px;
  text-align: center;
  text-decoration: none;
}

.workspace-switch a:hover {
  color: #0f172a;
}

.workspace-switch a.active {
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 5px 14px -5px rgba(14, 165, 233, 0.55);
}

.collapse-button {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #dbe3ec;
  border-radius: 6px;
  background: #f8fafc;
  color: #64748b;
  cursor: pointer;
}

.collapse-button:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.header-slogan {
  color: #334155;
  font-size: 17px;
  line-height: 1;
  white-space: nowrap;
}

.content {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 14px;
}

.content.immersive {
  height: 0;
  min-height: 0;
  flex: 1 1 0;
  padding: 0;
  overflow: hidden;
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

  .topbar > .forecast-account {
    display: none;
  }
}
</style>
