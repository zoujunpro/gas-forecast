<template>
  <div class="table-panel">
    <div v-if="$slots.filters || $slots.actions" class="table-toolbar">
      <div class="table-filters">
        <slot name="filters" />
      </div>
      <div v-if="$slots.actions" class="table-actions">
        <slot name="actions" />
      </div>
    </div>

    <slot />

    <slot name="footer" />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'AppTablePanel'
})
</script>

<style scoped>
.table-panel {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  padding: 14px;
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
}

.table-filters,
.table-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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
