<template>
  <div class="page-heading">
    <p class="page-breadcrumb">
      <template v-for="(item, index) in breadcrumbItems" :key="`${item}-${index}`">
        <span v-if="index" class="separator">›</span>
        <span>{{ item }}</span>
      </template>
    </p>
    <h1>{{ title }}</h1>
    <p v-if="description" class="page-description">{{ description }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getCurrentMenuPath } from '@/utils/auth'

const route = useRoute()

defineProps<{
  description?: string
}>()

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
  padding: 2px 0 0;
}

.page-breadcrumb {
  margin: 0 0 6px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.separator {
  color: #98a2b3;
  padding: 0 6px;
}

h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
  font-weight: 600;
}

.page-description {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 20px;
}
</style>
