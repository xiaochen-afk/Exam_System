<script setup>
defineProps({
  pending: Boolean,
  error: Error,
  empty: Boolean,
  emptyText: { type: String, default: '暂无符合条件的记录' },
})
defineEmits(['retry'])
</script>

<template>
  <div v-if="pending" class="feedback" role="status">正在加载…</div>
  <div v-else-if="error" class="feedback" role="alert">
    <p>
      {{ error.message }} <small v-if="error.requestId">（请求编号 {{ error.requestId }}）</small>
    </p>
    <button type="button" class="button outline" @click="$emit('retry')">重试</button>
  </div>
  <div v-else-if="empty" class="feedback">{{ emptyText }}</div>
  <slot v-else />
</template>
