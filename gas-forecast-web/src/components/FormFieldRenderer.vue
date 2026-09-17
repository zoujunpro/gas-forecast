<template>
  <el-select
    v-if="field.type === 'select'"
    v-model="model[field.prop]"
    class="form-control"
    clearable
    :multiple="field.multiple"
  >
    <el-option v-for="option in resolvedOptions" :key="option.value" :label="option.label" :value="option.value" />
  </el-select>
  <el-tree-select
    v-else-if="field.type === 'treeSelect'"
    v-model="model[field.prop]"
    class="form-control"
    clearable
    filterable
    check-strictly
    default-expand-all
    node-key="id"
    :data="treeData"
    :props="{ label: field.labelKey || 'label', children: 'children', value: 'id' }"
  />
  <el-radio-group v-else-if="field.type === 'radio'" v-model="model[field.prop]" class="type-segment">
    <el-radio-button v-for="option in resolvedOptions" :key="option.value" :label="option.value">
      {{ option.label }}
    </el-radio-button>
  </el-radio-group>
  <el-input-number
    v-else-if="field.type === 'number'"
    v-model="model[field.prop]"
    class="form-control"
    :min="field.min || 0"
    controls-position="right"
  />
  <el-switch
    v-else-if="field.type === 'switch'"
    v-model="model[field.prop]"
    :active-value="field.activeValue ?? 1"
    :inactive-value="field.inactiveValue ?? 0"
    :active-text="field.activeText"
    :inactive-text="field.inactiveText"
    inline-prompt
  />
  <slot v-else-if="field.type === 'tree'" />
  <el-input v-else v-model="model[field.prop]" clearable :type="field.inputType || 'text'" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BaseDataFieldConfig, Option, SystemFieldConfig } from '@/views/shared/managementTypes'

const props = defineProps<{
  field: BaseDataFieldConfig | SystemFieldConfig
  model: Record<string, any>
  optionMap?: Record<string, Option[]>
  treeData?: Record<string, any>[]
}>()

const resolvedOptions = computed(() => props.field.options || props.optionMap?.[props.field.optionKey || ''] || [])
</script>

<style scoped>
.form-control {
  width: 100%;
}
</style>
