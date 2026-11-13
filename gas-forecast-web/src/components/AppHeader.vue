<template>
  <header class="app-header">
    <button class="app-header__brand" type="button" @click="emit('home')">
      <img class="app-header__logo" src="/assets/dashboard/logo-transparent.png" alt="" />
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
      <span class="app-header__notice" role="img" aria-label="通知">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5l-2 3Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
          <path d="M10 20h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          <circle cx="19" cy="5" r="3.2" fill="#ef2525" stroke="#edf2ff" stroke-width="1.2" />
        </svg>
      </span>
      <span class="app-header__avatar" aria-hidden="true">
        <svg viewBox="0 0 36 36" fill="none">
          <circle cx="18" cy="18" r="18" fill="#0878f9" />
          <circle cx="18" cy="13" r="6" fill="#c9e3ff" />
          <path d="M7 32a11 11 0 0 1 22 0" fill="#c9e3ff" />
        </svg>
      </span>
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
  position: relative;
  isolation: isolate;
  overflow: hidden;
  height: 58px;
  min-height: 58px;
  flex: 0 0 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 27px 0 34px;
  border-bottom: 0;
  background: linear-gradient(97deg, #edf2ff, #e8eefb 53%, #b6d0f9 77%, #e9f0ff);
  box-shadow: 0 2px 9px #7297e33d;
  color: #080f3d;
}
.app-header::before,.app-header::after{position:absolute;z-index:-1;border-radius:50% 50% 0 0/100% 100% 0 0;content:"";pointer-events:none}
.app-header::before{width:615px;height:117px;right:-59px;top:10px;transform:rotate(8deg);background:linear-gradient(110deg,#f0f7ffb8,#8db9f34d 59%,#f8fbff94);box-shadow:6px -12px 0 #eaf4ff59,34px 12px 0 #c5dcff54}
.app-header::after{width:492px;height:98px;right:-48px;top:34px;transform:rotate(6deg);background:linear-gradient(100deg,#d7eaff57,#74a9ee4f,#ecf5ff78);box-shadow:24px -11px 0 #9cc5f542}

.app-header__brand {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #080f3d;
  cursor: pointer;
}

.app-header__brand strong { font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", sans-serif; font-size: 24px; font-weight: 700; line-height: 1.2; white-space: nowrap; }
.app-header__logo { width: 36px; height: 44px; object-fit: contain; }

.app-header__navigation {
  position: absolute;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0;
  transform: translateX(-50%);
}

.app-header__navigation :deep(a) {
  width: clamp(116px, 8.04vw, 139px);
  padding: 11px 8px;
  border-radius: 14px;
  color: #080f3d;
  font-size: 17px;
  font-weight: 600;
  line-height: 20px;
  text-align: center;
  text-decoration: none;
}

.app-header__navigation :deep(a:hover) { color: #0057ff; background: #f1f5ff; }
.app-header__navigation :deep(a.active) {
  border-bottom: 2px solid #0657ff;
  background: #f1f5ff;
  color: #0057ff;
  font-weight: 600;
  box-shadow: 0 3px 10px #6498ed26;
}

.app-header__account { display: flex; align-items: center; gap: 10px; color: #080f3d; font-size: 17px; font-weight: 600; }
.app-header__notice { display: inline-flex; width: 26px; height: 26px; margin-right: 14px; align-items: center; justify-content: center; color: #516b9d; }
.app-header__notice svg { width: 27px; height: 27px; display: block; }
.app-header__avatar { display: inline-flex; width: 36px; height: 36px; align-items: center; justify-content: center; border-radius: 50%; }
.app-header__avatar svg { display: block; width: 100%; height: 100%; }
.app-header__user { color:#080f3d;line-height:36px;white-space:nowrap}.app-header__user small{display:none}
.app-header__account button{width:18px;overflow:hidden;padding:0;border:0;background:transparent;color:transparent;cursor:pointer;font-size:0;white-space:nowrap}
.app-header__account button::before{color:#080f3d;content:"⌄";font-size:15px;font-weight:600}
.app-header__account button:hover { color: #2563eb; }

@media (max-width: 1000px) {
  .app-header__navigation { position: static; margin-left: auto; transform: none; }
  .app-header__user { display: none; }
}

@media (max-width: 980px) { .app-header__account { gap: 7px; } }
@media (max-width: 760px) { .app-header { padding: 0 14px; } .app-header__brand strong, .app-header__navigation, .app-header__notice, .app-header__user, .app-header__account button { display: none; } }
</style>
