<template>
  <SystemManagement :page-config="rolesSystemConfig" />
</template>

<script setup lang="ts">
import SystemManagement from './SystemManagement.vue'
import { rolesSystemConfig } from './systemManagementConfigs'

defineOptions({ name: 'SystemRoleManagement' })
</script>
