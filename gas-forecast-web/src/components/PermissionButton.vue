<template>
  <el-button v-if="allowed" v-bind="$attrs">
    <slot />
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { hasPermission } from '@/utils/auth'

defineOptions({
  name: 'PermissionButton',
  inheritAttrs: false
})

const props = defineProps<{
  permission?: string
}>()

const allowed = computed(() => !props.permission || hasPermission(props.permission))
</script>
