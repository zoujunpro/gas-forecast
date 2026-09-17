<template>
  <CrudManagementPage ref="crudRef" :page-config="pageConfig">
    <template #table-actions="{ reload }">
      <slot name="table-actions" :reload="reload" />
    </template>
  </CrudManagementPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CrudManagementPage from '@/components/CrudManagementPage.vue'
import type { BaseDataPageConfig as PageConfig } from '../../shared/managementTypes'

defineProps<{
  pageConfig: PageConfig
}>()

const crudRef = ref<InstanceType<typeof CrudManagementPage>>()

defineExpose({
  loadData: () => crudRef.value?.loadData()
})
</script>
