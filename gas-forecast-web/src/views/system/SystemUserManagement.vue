<template>
  <SystemManagement :page-config="usersSystemConfig" />
</template>

<script setup lang="ts">
import SystemManagement from './SystemManagement.vue'
import { usersSystemConfig } from './systemManagementConfigs'

defineOptions({ name: 'SystemUserManagement' })
</script>
