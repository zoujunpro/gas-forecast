<template>
  <BaseDataManagement :page-config="modelFeatureDefinitionConfig" />
</template>

<script setup lang="ts">
import BaseDataManagement from '@/views/business/base/BaseDataManagement.vue'
import { modelFeatureDefinitionConfig } from './modelManagementConfigs'

defineOptions({ name: 'ModelFeatureDefinitionManagement' })
</script>
