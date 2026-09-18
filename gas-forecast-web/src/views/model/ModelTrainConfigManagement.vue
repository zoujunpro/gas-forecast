<template>
  <BaseDataManagement :page-config="modelTrainConfigConfig" />
</template>

<script setup lang="ts">
import BaseDataManagement from '@/views/business/base/BaseDataManagement.vue'
import { modelTrainConfigConfig } from './modelManagementConfigs'

defineOptions({ name: 'ModelTrainConfigManagement' })
</script>
