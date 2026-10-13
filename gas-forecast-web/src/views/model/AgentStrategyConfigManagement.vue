<template>
  <BaseDataManagement :page-config="agentStrategyConfig" />
</template>

<script setup lang="ts">
import BaseDataManagement from '@/views/business/base/BaseDataManagement.vue'
import { agentStrategyConfig } from './modelManagementConfigs'

defineOptions({ name: 'AgentStrategyConfigManagement' })
</script>
