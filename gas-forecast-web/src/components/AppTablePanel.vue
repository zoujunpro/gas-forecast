<template>
  <div class="table-panel">
    <div v-if="$slots.filters || $slots.actions || updatedAt" class="table-toolbar">
      <div class="table-filters">
        <slot name="filters" />
      </div>
      <div v-if="$slots.actions" class="table-actions">
        <slot name="actions" />
      </div>
      <span v-if="updatedAt" class="table-updated">更新于 {{ updatedAt }}</span>
    </div>

    <slot />

    <slot name="footer" />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'AppTablePanel'
})

defineProps<{
  updatedAt?: string
}>()
</script>

<style scoped>
.table-panel {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 16px;
  overflow: hidden;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.025);
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--app-border-soft);
}

.table-filters,
.table-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-updated {
  margin-left: auto;
  color: var(--app-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.table-actions + .table-updated {
  margin-left: 2px;
}

.table-actions :deep(.el-button) {
  min-width: 72px;
}

.table-actions {
  justify-content: flex-end;
  flex-shrink: 0;
}

@media (max-width: 760px) {
  .table-toolbar,
  .table-filters {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
