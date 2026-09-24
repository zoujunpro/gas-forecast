<template>
  <el-button v-if="allowed" v-bind="$attrs">
    <slot />
  </el-button>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { hasPermission, PROFILE_UPDATED_EVENT } from '@/utils/auth'

defineOptions({
  name: 'PermissionButton',
  inheritAttrs: false
})

const props = defineProps<{
  permission?: string
}>()

const profileVersion = ref(0)
const refreshPermission = () => {
  profileVersion.value += 1
}
const allowed = computed(() => {
  void profileVersion.value
  return !props.permission || hasPermission(props.permission)
})

onMounted(() => window.addEventListener(PROFILE_UPDATED_EVENT, refreshPermission))
onBeforeUnmount(() => window.removeEventListener(PROFILE_UPDATED_EVENT, refreshPermission))
</script>
