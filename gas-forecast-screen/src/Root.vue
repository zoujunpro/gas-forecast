<template><WorkBuddy v-if="route.startsWith('#/workbuddy')" :key="route"/><App v-else/></template>
<script setup>
import {ref,onBeforeUnmount} from 'vue'
import App from './App.vue'
import WorkBuddy from './WorkBuddy.vue'
const route=ref(location.hash);const update=()=>route.value=location.hash;window.addEventListener('hashchange',update);onBeforeUnmount(()=>window.removeEventListener('hashchange',update))
</script>
