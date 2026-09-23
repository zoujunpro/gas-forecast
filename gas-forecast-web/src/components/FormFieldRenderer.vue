<template>
  <el-select
    v-if="field.type === 'select'"
    v-model="model[field.prop]"
    class="form-control"
    clearable
    filterable
    :placeholder="field.placeholder || `请选择${field.label}`"
    :multiple="field.multiple"
    @change="handleSelectChange"
  >
    <el-option v-for="option in resolvedOptions" :key="option.value" :label="option.label" :value="option.value" />
  </el-select>
  <el-select
    v-else-if="field.type === 'iconSelect'"
    v-model="model[field.prop]"
    class="form-control icon-select"
    clearable
    filterable
    :placeholder="field.placeholder || `请选择${field.label}`"
  >
    <template #label="{ label, value }">
      <span class="icon-selected-value">
        <el-icon><component :is="resolveMenuIcon(String(value))" /></el-icon>
        <span>{{ iconDisplayLabel(String(value), String(label)) }}</span>
      </span>
    </template>
    <el-option
      v-for="option in resolvedOptions"
      :key="option.value"
      :label="iconSearchLabel(option)"
      :value="option.value"
    >
      <span class="icon-option">
        <el-icon><component :is="resolveMenuIcon(String(option.value))" /></el-icon>
        <span>{{ option.label }}</span>
        <code>{{ option.value }}</code>
      </span>
    </el-option>
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
  <div v-else-if="field.type === 'granularityDate' && currentGranularity === 'TENDAY'" class="tenday-picker">
    <el-date-picker
      v-model="tendayMonth"
      class="tenday-month"
      type="month"
      value-format="YYYY-MM"
      placeholder="选择月份"
      @change="syncTendayValue"
    />
    <el-select v-model="tendayPart" class="tenday-part" placeholder="选择旬" @change="syncTendayValue">
      <el-option label="上旬" value="01" />
      <el-option label="中旬" value="11" />
      <el-option label="下旬" value="21" />
    </el-select>
  </div>
  <el-date-picker
    v-else-if="field.type === 'granularityDate' && currentGranularity === 'MONTH'"
    v-model="monthValue"
    class="form-control"
    type="month"
    value-format="YYYY-MM"
    placeholder="选择月份"
    @change="syncMonthValue"
  />
  <el-date-picker
    v-else-if="field.type === 'granularityDate'"
    v-model="model[field.prop]"
    class="form-control"
    type="date"
    value-format="YYYY-MM-DD"
    placeholder="选择日期"
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
  <el-input
    v-else
    v-model="model[field.prop]"
    clearable
    :readonly="field.readonly"
    :type="field.inputType || 'text'"
    :placeholder="field.placeholder || `请输入${field.label}`"
    :rows="field.rows"
    :maxlength="field.maxLength"
    :show-word-limit="field.showWordLimit"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BaseDataFieldConfig, Option, SystemFieldConfig } from '@/views/shared/managementTypes'
import { resolveMenuIcon } from '@/utils/menuIcons'

const props = defineProps<{
  field: BaseDataFieldConfig | SystemFieldConfig
  model: Record<string, any>
  optionMap?: Record<string, Option[]>
  treeData?: Record<string, any>[]
}>()

const emit = defineEmits<{
  optionSelect: [field: BaseDataFieldConfig | SystemFieldConfig, option?: Option]
}>()

const resolvedOptions = computed(
  () => props.field.options || props.optionMap?.[props.field.optionKey || props.field.prop] || []
)
const iconSearchLabel = (option: Option) => `${option.label} ${option.value}`
const iconDisplayLabel = (value: string, fallback: string) =>
  resolvedOptions.value.find((option) => String(option.value) === value)?.label || fallback
const currentGranularity = computed(() => {
  if (props.field.type !== 'granularityDate') {
    return ''
  }
  return props.model[props.field.granularityProp || 'timeGranularity'] || 'DAY'
})
const tendayMonth = ref('')
const tendayPart = ref('01')
const monthValue = ref('')

const syncTendayControls = (value: unknown) => {
  if (typeof value !== 'string') {
    tendayMonth.value = ''
    tendayPart.value = '01'
    return
  }
  const matched = value.match(/^(\d{4}-\d{2})(?:-(01|11|21))?/)
  tendayMonth.value = matched?.[1] || ''
  tendayPart.value = matched?.[2] || '01'
}

const syncTendayValue = () => {
  props.model[props.field.prop] = tendayMonth.value ? `${tendayMonth.value}-${tendayPart.value}` : ''
}

const isEndDateField = computed(() => /end/i.test(props.field.prop))

const getMonthEndDay = (year: number, month: number) => {
  return new Date(year, month, 0).getDate().toString().padStart(2, '0')
}

const syncMonthControl = (value: unknown) => {
  monthValue.value = typeof value === 'string' ? value.slice(0, 7) : ''
}

const syncMonthValue = () => {
  if (!monthValue.value) {
    props.model[props.field.prop] = ''
    return
  }
  const [year, month] = monthValue.value.split('-').map(Number)
  const day = isEndDateField.value ? getMonthEndDay(year, month) : '01'
  props.model[props.field.prop] = `${monthValue.value}-${day}`
}

watch(
  () => props.model[props.field.prop],
  (value) => {
    if (props.field.type !== 'granularityDate') {
      return
    }
    if (currentGranularity.value === 'TENDAY') {
      syncTendayControls(value)
    }
    if (currentGranularity.value === 'MONTH') {
      syncMonthControl(value)
    }
  },
  { immediate: true }
)

watch(currentGranularity, (granularity) => {
  if (props.field.type !== 'granularityDate') {
    return
  }
  props.model[props.field.prop] = ''
  if (granularity === 'TENDAY') {
    syncTendayControls('')
  }
  if (granularity === 'MONTH') {
    syncMonthControl('')
  }
})

const handleSelectChange = (value: string | number | Array<string | number>) => {
  const selected = resolvedOptions.value.find((option) => option.value === value)
  emit('optionSelect', props.field, selected)
}
</script>

<style scoped>
.form-control {
  width: 100%;
}

.icon-option {
  display: flex;
  align-items: center;
  gap: 9px;
}

.icon-selected-value {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
}

.icon-selected-value .el-icon {
  flex: 0 0 auto;
  color: var(--el-color-primary);
  font-size: 16px;
}

.icon-option .el-icon {
  flex: 0 0 auto;
  color: var(--el-color-primary);
  font-size: 17px;
}

.icon-option code {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.tenday-picker {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 116px;
  gap: 8px;
  width: 100%;
}

.tenday-month,
.tenday-part {
  width: 100%;
}
</style>
