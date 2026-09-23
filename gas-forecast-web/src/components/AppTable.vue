<template>
  <el-table class="app-table" v-bind="$attrs">
    <slot />
    <template v-if="$slots.empty" #empty>
      <slot name="empty" />
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
</script>

<style scoped>
.app-table {
  font-size: 14px;
}

.app-table :deep(th.el-table__cell) {
  background: #f8fafc;
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
}

.app-table :deep(td.el-table__cell) {
  color: #344054;
  font-size: 14px;
  font-weight: 500;
}

.app-table :deep(.cell) {
  line-height: 22px;
}

.app-table :deep(.el-table__cell) {
  vertical-align: middle;
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
  color: #475467;
  font-family: inherit;
  font-size: 12px;
}

.app-table :deep(.action-column .el-button) {
  font-size: 13px;
  font-weight: 500;
}
</style>
