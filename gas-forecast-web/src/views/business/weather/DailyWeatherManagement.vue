<template>
  <BaseDataManagement :page-config="dailyWeatherDataConfig" />
</template>

<script setup lang="ts">
import BaseDataManagement from '@/views/business/base/BaseDataManagement.vue'
import { dailyWeatherDataConfig } from '@/views/business/base/baseDataManagementConfigs'

defineOptions({ name: 'DailyWeatherManagement' })
</script>
