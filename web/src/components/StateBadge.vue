<script setup>
import { states, stateTone } from '@/domain/presentation.js'
defineProps({ value: { type: String, required: true } })
</script>

<template>
  <span class="badge" :data-tone="stateTone(value)">{{ states[value] ?? value }}</span>
</template>
