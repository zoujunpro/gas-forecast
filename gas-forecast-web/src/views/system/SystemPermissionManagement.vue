<template>
  <SystemManagement :page-config="permissionsSystemConfig" />
</template>

<script setup lang="ts">
import SystemManagement from './SystemManagement.vue'
import { permissionsSystemConfig } from './systemManagementConfigs'

defineOptions({ name: 'SystemPermissionManagement' })
</script>
