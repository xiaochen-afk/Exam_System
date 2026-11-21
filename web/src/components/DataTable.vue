<script setup>
import StateBadge from './StateBadge.vue'
import { formatDate } from '@/domain/presentation.js'

defineProps({ columns: { type: Array, required: true }, rows: { type: Array, required: true } })

function valueOf(row, key) {
  const value = row[key]
  if (key === 'registrationOpen') return value ? '已开放' : '已暂停'
  return key.endsWith('At') ? formatDate(value, true) : (value ?? '—')
}
</script>

<template>
  <div class="table-scroll" tabindex="0" aria-label="数据列表">
    <table class="data-table">
      <thead>
        <tr>
          <th v-for="[key, label] in columns" :key="key" scope="col">{{ label }}</th>
          <th v-if="$slots.actions" scope="col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td v-for="[key] in columns" :key="key">
            <slot :name="key" :row="row">
              <StateBadge
                v-if="
                  [
                    'status',
                    'qualificationStatus',
                    'result',
                    'candidateType',
                    'arrangementStatus',
                    'scoreStatus',
                  ].includes(key) && row[key]
                "
                :value="row[key]"
              />
              <template v-else>{{ valueOf(row, key) }}</template>
            </slot>
          </td>
          <td v-if="$slots.actions" class="row-actions"><slot name="actions" :row="row" /></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
