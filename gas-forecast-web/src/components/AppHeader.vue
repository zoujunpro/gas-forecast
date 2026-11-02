<template>
  <header class="app-header">
    <button class="app-header__brand" type="button" @click="emit('home')">
      <img class="app-header__logo" src="/assets/dashboard/logo.png" alt="" />
      <strong>天然气市场需求智能预测平台</strong>
    </button>

    <WorkspaceNavigation
      class="app-header__navigation"
      :menus="menus"
      :target="target"
      :label="label"
      :active="active"
    />

    <div class="app-header__account">
      <img class="app-header__notice" src="/assets/dashboard/notification.png" alt="通知" />
      <img class="app-header__avatar" src="/assets/dashboard/avatar.png" alt="" />
      <span class="app-header__user">{{ welcomeName }}<small>系统管理员</small></span>
      <button type="button" @click="emit('logout')">退出</button>
    </div>
  </header>
</template>

<script setup lang="ts">
import WorkspaceNavigation from '@/components/WorkspaceNavigation.vue'
import type { AuthMenu } from '@/utils/auth'

defineProps<{
  menus: AuthMenu[]
  target: (menu: AuthMenu) => string
  label: (menu: AuthMenu) => string
  active: (menu: AuthMenu) => boolean
  welcomeName: string
}>()

const emit = defineEmits<{
  home: []
  logout: []
}>()
</script>

<style scoped>
.app-header {
  height: 73px;
  min-height: 73px;
  flex: 0 0 73px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 24px;
  border-bottom: 1px solid #e2e8f0;
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 1px 3px rgba(15, 23, 42, .04);
  color: #081333;
}

.app-header__brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #172033;
  cursor: pointer;
}

.app-header__brand strong { font-family: "Microsoft YaHei", "Noto Sans CJK SC", Arial, sans-serif; font-size: 20px; font-weight: 750; line-height: 1.2; white-space: nowrap; }
.app-header__logo { width: 36px; height: 44px; object-fit: contain; }

.app-header__navigation {
  position: absolute;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 42px;
  padding: 0;
  transform: translateX(-50%);
}

.app-header__navigation :deep(a) {
  padding: 11px 20px;
  border-radius: 9px;
  color: #64748b;
  font-size: 15px;
  font-weight: 500;
  line-height: 20px;
  text-align: center;
  text-decoration: none;
}

.app-header__navigation :deep(a:hover) { color: #1d4ed8; background: #f5f9ff; }
.app-header__navigation :deep(a.active) {
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 650;
  box-shadow: inset 0 -2px 0 #2563eb;
}

.app-header__account { display: flex; align-items: center; gap: 14px; color: #334155; font-size: 14px; }
.app-header__notice { width: 30px; height: 34px; object-fit: contain; }
.app-header__avatar { width: 42px; height: 42px; object-fit: contain; }
.app-header__user small { display: block; color: #94a3b8; font-size: 11px; }
.app-header__account button { padding: 0; border: 0; background: transparent; color: #64748b; cursor: pointer; font: inherit; }
.app-header__account button:hover { color: #2563eb; }

@media (max-width: 1000px) {
  .app-header__navigation { position: static; margin-left: auto; transform: none; }
  .app-header__user { display: none; }
}

@media (max-width: 980px) { .app-header__account { gap: 7px; } }
@media (max-width: 760px) { .app-header { padding: 0 14px; } .app-header__brand strong, .app-header__navigation, .app-header__notice, .app-header__user, .app-header__account button { display: none; } }
</style>
