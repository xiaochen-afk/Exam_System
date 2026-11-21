<script setup>
defineProps({ title: { type: String, required: true }, summary: String })
</script>

<template>
  <header class="admin-title">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="summary">{{ summary }}</p>
    </div>
    <slot />
  </header>
</template>
