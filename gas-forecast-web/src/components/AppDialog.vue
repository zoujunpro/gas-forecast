<template>
  <el-dialog v-model="visible" class="app-dialog" v-bind="$attrs">
    <template #header>
      <slot name="header">
        <div class="app-dialog-head">
          <p v-if="eyebrow">{{ eyebrow }}</p>
          <h2>{{ title }}</h2>
        </div>
      </slot>
    </template>

    <slot />

    <template v-if="$slots.footer" #footer>
      <div class="app-dialog-footer">
        <slot name="footer" />
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineOptions({
  name: 'AppDialog',
  inheritAttrs: false
})

defineProps<{
  eyebrow?: string
  title: string
}>()

const visible = defineModel<boolean>({ default: false })
</script>

<style scoped>
:global(.app-dialog) {
  border-radius: var(--app-radius);
  overflow: hidden;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.16);
}

:global(.app-dialog .el-dialog__header) {
  margin: 0;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--app-border);
}

:global(.app-dialog .el-dialog__body) {
  max-height: min(64vh, 720px);
  padding: 20px 24px 8px;
  overflow: auto;
}

:global(.app-dialog .el-dialog__footer) {
  padding: 14px 24px 20px;
  border-top: 1px solid var(--app-border);
}

.app-dialog-head p {
  margin: 0 0 6px;
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 600;
}

.app-dialog-head h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
  line-height: 1.2;
}

.app-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:global(.app-dialog .el-dialog__headerbtn) {
  top: 18px;
  right: 18px;
  width: 28px;
  height: 28px;
  border-radius: 7px;
}

:global(.app-dialog .el-dialog__headerbtn:hover) {
  background: #f1f5f9;
}
</style>
