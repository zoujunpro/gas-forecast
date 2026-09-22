<template>
  <div class="app-pagination">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      layout="total, sizes, prev, pager, next"
      :page-sizes="pageSizes"
      :total="total"
      @current-change="$emit('current-change')"
      @size-change="$emit('size-change')"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'AppPagination'
})

withDefaults(
  defineProps<{
    total: number
    pageSizes?: number[]
  }>(),
  {
    pageSizes: () => [10, 20, 50]
  }
)

defineEmits<{
  (event: 'current-change'): void
  (event: 'size-change'): void
}>()

const currentPage = defineModel<number>('currentPage', { required: true })
const pageSize = defineModel<number>('pageSize', { required: true })
</script>

<style scoped>
.app-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
</style>
