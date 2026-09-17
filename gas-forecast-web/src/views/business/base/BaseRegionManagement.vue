<template>
  <BaseDataManagement :page-config="regionsDataConfig" />
</template>

<script setup lang="ts">
import BaseDataManagement from './BaseDataManagement.vue'
import { regionsDataConfig } from './baseDataManagementConfigs'

defineOptions({ name: 'BaseRegionManagement' })
</script>
