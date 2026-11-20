<template><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="graphIconPaths[name]||graphIconPaths.graph" :fill="name==='send'?'currentColor':'none'" :stroke="name==='send'?'none':'currentColor'"/></svg></template>
<script setup>
import {graphIconPaths} from './graphIcons.js'
defineProps({name:String})
</script>
