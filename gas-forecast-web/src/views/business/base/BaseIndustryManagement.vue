<template>
  <BaseDataManagement :page-config="industriesDataConfig" />
</template>

<script setup lang="ts">
import BaseDataManagement from './BaseDataManagement.vue'
import { industriesDataConfig } from './baseDataManagementConfigs'

defineOptions({ name: 'BaseIndustryManagement' })
</script>
