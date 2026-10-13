<template>
  <div />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

defineOptions({ name: 'ModelTrainTaskManagement' })

const router = useRouter()
onMounted(() => router.replace('/model/train-config'))
</script>
