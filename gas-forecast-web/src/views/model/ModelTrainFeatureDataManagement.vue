<template>
  <BaseDataManagement :page-config="modelTrainFeatureDataConfig" />
</template>

<script setup lang="ts">
import BaseDataManagement from '@/views/business/base/BaseDataManagement.vue'
import { modelTrainFeatureDataConfig } from './modelManagementConfigs'

defineOptions({ name: 'ModelTrainFeatureDataManagement' })
</script>
