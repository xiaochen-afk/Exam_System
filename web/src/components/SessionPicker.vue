<script setup>
import { loadOptions } from '@/composables/useAdminList.js'
import { useAsync } from '@/composables/useAsync.js'

const props = defineProps({ modelValue: String, context: { type: String, required: true } })
defineEmits(['update:modelValue'])
const { result, pending, error, reload } = useAsync((signal) =>
  loadOptions(['sessions'], signal, { context: props.context }),
)
reload()
</script>

<template>
  <div class="filter-bar panel">
    <label
      >考试场次<select
        :value="modelValue"
        :disabled="pending || Boolean(error)"
        @change="$emit('update:modelValue', $event.target.value)"
      >
        <option value="">{{ pending ? '正在加载场次…' : '请选择考试场次' }}</option>
        <option v-for="item in result?.sessions" :key="item.id" :value="item.id">
          {{ item.name }}
        </option>
      </select></label
    >
    <span v-if="!pending && !error && result?.sessions.length === 0" class="muted"
      >当前授权范围暂无可处理的场次</span
    >
    <p v-if="error" class="error-text" role="alert">
      {{ error.message }} <button class="link-button" @click="reload">重试</button>
    </p>
    <slot />
  </div>
</template>
