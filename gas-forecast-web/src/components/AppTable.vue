<template>
  <el-table class="app-table" v-bind="$attrs">
    <slot />
    <template v-if="$slots.empty" #empty>
      <slot name="empty" />
    </template>
    <template v-else #empty>
      <el-empty :image-size="52" :description="emptyDescription" />
    </template>
    <template v-if="$slots.append" #append>
      <slot name="append" />
    </template>
  </el-table>
</template>

<script setup lang="ts">
defineOptions({
  name: 'AppTable',
  inheritAttrs: false
})

withDefaults(defineProps<{
  emptyDescription?: string
}>(), {
  emptyDescription: '暂无数据'
})
</script>

<style scoped>
.app-table {
  font-size: 14px;
}

.app-table :deep(th.el-table__cell) {
  background: #f6f8fc;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.app-table :deep(td.el-table__cell) {
  color: var(--app-text-secondary);
  font-size: 14px;
  font-weight: 500;
}

.app-table :deep(.cell) {
  line-height: 22px;
}

.app-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.app-table :deep(th.el-table__cell) {
  height: 42px;
}

.app-table :deep(td.el-table__cell) {
  height: 44px;
}

.app-table :deep(.el-table__row--striped td.el-table__cell) {
  background: #fbfcfe;
}

.app-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: #f5f9ff !important;
}

.app-table :deep(.el-table__empty-block) {
  min-height: 220px;
}

.app-table :deep(.el-table__empty-text) {
  color: var(--app-text-muted);
  font-size: 13px;
}

.app-table :deep(.el-table__fixed),
.app-table :deep(.el-table__fixed-right),
.app-table :deep(.el-table__fixed-column--left),
.app-table :deep(.el-table__fixed-column--right) {
  box-shadow: none !important;
}

.app-table :deep(.el-table__fixed-column--left::before),
.app-table :deep(.el-table__fixed-column--right::before) {
  display: none !important;
}

.app-table :deep(.el-table__header .el-table__cell:first-child),
.app-table :deep(.el-table__body .el-table__cell:first-child) {
  border-right: 0 !important;
  box-shadow: none !important;
}

.app-table :deep(.id-column),
.app-table :deep(.id-column.is-leaf),
.app-table :deep(td.id-column),
.app-table :deep(th.id-column) {
  border-right: 0 !important;
  box-shadow: none !important;
}

.app-table :deep(.id-column::before),
.app-table :deep(.id-column::after) {
  display: none !important;
}

.app-table :deep(.el-table__header .el-table__cell.el-table-fixed-column--right),
.app-table :deep(.el-table__body .el-table__cell.el-table-fixed-column--right) {
  border-left-color: transparent;
  box-shadow: none !important;
}

.app-table :deep(.action-column),
.app-table :deep(.action-column.is-leaf),
.app-table :deep(td.action-column),
.app-table :deep(th.action-column) {
  border-left: 0 !important;
  box-shadow: none !important;
}

.app-table :deep(.action-column::before),
.app-table :deep(.action-column::after) {
  display: none !important;
}

.app-table :deep(.cell-text) {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-table :deep(.mono-text) {
  color: var(--app-text-secondary);
  font-family: inherit;
  font-size: 13px;
}

.app-table :deep(.action-column .el-button) {
  font-size: 13px;
  font-weight: 500;
}

.app-table :deep(.action-column .el-button + .el-button) { margin-left: 10px; }

.app-table :deep(.action-column .el-button--success.is-link) { color: var(--app-primary); }
.app-table :deep(.action-column .el-button--info.is-link) { color: var(--app-text-secondary); }
.app-table :deep(.action-column .el-button--danger.is-link) { color: var(--app-danger); }
</style>
