<template>
  <nav class="workspace-navigation" aria-label="中心切换">
    <RouterLink
      v-for="menu in menus"
      :key="menu.id"
      :to="target(menu)"
      :class="{ active: active(menu) }"
    >{{ label(menu) }}</RouterLink>
  </nav>
</template>

<script setup lang="ts">
import type { AuthMenu } from '@/utils/auth'

defineProps<{
  menus: AuthMenu[]
  target: (menu: AuthMenu) => string
  label: (menu: AuthMenu) => string
  active: (menu: AuthMenu) => boolean
}>()
</script>

<style scoped>
.workspace-navigation {
  display: flex;
  align-items: center;
  gap: 42px;
}

.workspace-navigation a {
  padding: 11px 20px;
  border-radius: 9px;
  color: #64748b;
  font-family: "Microsoft YaHei", "Noto Sans CJK SC", Arial, sans-serif;
  font-size: 15px;
  font-weight: 500;
  line-height: 20px;
  text-align: center;
  text-decoration: none;
  white-space: nowrap;
  transition: color .2s, background .2s, box-shadow .2s;
}

.workspace-navigation a:hover {
  color: #1d4ed8;
  background: #f5f9ff;
}

.workspace-navigation a.active {
  background: #eff6ff;
  box-shadow: inset 0 -2px 0 #2563eb;
  color: #1d4ed8;
  font-weight: 650;
}

@media (max-width: 850px) {
  .workspace-navigation {
    width: 100%;
    justify-content: center;
    gap: 4px;
  }

  .workspace-navigation a {
    padding: 12px;
    font-size: 15px;
  }
}
</style>
