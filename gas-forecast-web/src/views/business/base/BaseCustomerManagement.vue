<template>
  <BaseDataManagement :page-config="customersDataConfig" />
</template>

<script setup lang="ts">
import BaseDataManagement from './BaseDataManagement.vue'
import { customersDataConfig } from './baseDataManagementConfigs'

defineOptions({ name: 'BaseCustomerManagement' })
</script>
