<template>
  <template v-for="item in visibleMenus" :key="item.id">
    <el-sub-menu v-if="visibleChildren(item).length" :index="menuIndex(item)">
      <template #title>
        <el-icon v-if="item.icon">
          <component :is="resolveMenuIcon(item.icon)" />
        </el-icon>
        <span>{{ item.name }}</span>
      </template>
      <SidebarMenu :menus="visibleChildren(item)" :collapsed="collapsed" />
    </el-sub-menu>
    <el-menu-item v-else-if="item.path" :index="item.path">
      <el-icon v-if="item.icon">
        <component :is="resolveMenuIcon(item.icon)" />
      </el-icon>
      <template #title>
        <span>{{ item.name }}</span>
      </template>
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AuthMenu } from '@/utils/auth'
import { resolveMenuIcon } from '@/utils/menuIcons'

defineOptions({ name: 'SidebarMenu' })

const props = defineProps<{
  menus: AuthMenu[]
  collapsed: boolean
}>()

const visibleMenus = computed(() => props.menus.filter((item) => !item.hidden))

const visibleChildren = (item: AuthMenu) => (item.children || []).filter((child) => !child.hidden)

const menuIndex = (item: AuthMenu) => item.path || String(item.id)
</script>
