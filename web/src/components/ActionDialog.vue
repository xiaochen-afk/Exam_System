<script setup>
import { ElDialog } from 'element-plus'
import 'element-plus/theme-chalk/el-dialog.css'
import 'element-plus/theme-chalk/el-overlay.css'

defineProps({ modelValue: Boolean, title: String, busy: Boolean, error: String })
defineEmits(['update:modelValue'])
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="title"
    width="min(92vw, 640px)"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p v-if="error" role="alert" class="inline-error">{{ error }}</p>
    <slot />
  </ElDialog>
</template>
