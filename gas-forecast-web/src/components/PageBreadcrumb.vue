<template>
  <div class="page-heading">
    <p class="page-breadcrumb">
      <template v-for="(item, index) in breadcrumbItems" :key="`${item}-${index}`">
        <span v-if="index" class="separator">›</span>
        <span>{{ item }}</span>
      </template>
    </p>
    <h1><el-icon><Collection /></el-icon>{{ title }}</h1>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Collection } from '@element-plus/icons-vue'
import { getCurrentMenuPath } from '@/utils/auth'

const route = useRoute()

const breadcrumbItems = computed(() => {
  const menuItems = getCurrentMenuPath(route.path).map((item) => item.name)
  if (menuItems.length) {
    return menuItems
  }
  return [String(route.meta.title || '功能页面')]
})

const title = computed(
  () => breadcrumbItems.value[breadcrumbItems.value.length - 1] || String(route.meta.title || '功能页面')
)
</script>

<style scoped>
.page-heading {
  padding: 0;
  /* 与下方首个业务卡片保留 10px 的有效间距（父容器统一 gap 为 14px）。 */
  margin-bottom: -4px;
}

.page-breadcrumb {
  margin: 0 0 5px;
  color: #94a3b8;
  font-size: 12px;
}

.separator {
  color: var(--app-text-muted);
  padding: 0 6px;
}

h1 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -.2px;
}

h1 .el-icon {
  width: 24px;
  height: 24px;
  padding: 5px;
  border-radius: 7px;
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-size: 14px;
}

</style>
