<template>
  <SystemManagement :page-config="departmentsSystemConfig" />
</template>

<script setup lang="ts">
import SystemManagement from './SystemManagement.vue'
import { departmentsSystemConfig } from './systemManagementConfigs'

defineOptions({ name: 'SystemDepartmentManagement' })
</script>
