<script setup>
import { computed, reactive } from 'vue'
import { toLocalInput } from '@/domain/presentation.js'
import { loadOptions } from '@/composables/useAdminList.js'
import { useAsync } from '@/composables/useAsync.js'
import FetchState from './FetchState.vue'

const props = defineProps({
  fields: { type: Array, required: true },
  row: Object,
  busy: Boolean,
  resource: String,
})
const emit = defineEmits(['save', 'cancel'])
const shown = computed(() => props.fields.filter((field) => !field.createOnly || !props.row))
const form = reactive(
  Object.fromEntries(
    shown.value.map((field) => {
      const value = props.row?.[field.key]
      if (field.type === 'multiselect') return [field.key, [...(value ?? [])]]
      if (field.type === 'datetime-local') return [field.key, toLocalInput(value)]
      return [field.key, value ?? field.initial ?? '']
    }),
  ),
)
const { result, pending, error, reload } = useAsync((signal) =>
  loadOptions(
    shown.value.filter((field) => field.lookup).map((field) => field.lookup),
    signal,
    { context: props.resource },
  ),
)
reload()

function choices(field) {
  if (!field.lookup) return Object.entries(field.choices).map(([id, name]) => ({ id, name }))
  const available = result.value?.[field.lookup] ?? []
  const selected = field.type === 'multiselect' ? form[field.key] : [form[field.key]]
  // Existing historical or inactive references still need a readable option when editing.
  const historical = selected.filter((id) => id && !available.some((item) => item.id === id))
  return [
    ...available,
    ...historical.map((id) => ({ id, name: props.row?.[field.key.replace(/Id$/, 'Name')] ?? id })),
  ]
}

function locked(field) {
  return (
    props.busy ||
    (props.row && field.immutable) ||
    (props.row?.registrationCount > 0 && field.lockAfterRegistration)
  )
}

function submit() {
  emit(
    'save',
    Object.fromEntries(
      shown.value.map((field) => {
        const value = form[field.key]
        if (field.type === 'datetime-local') {
          // 未修改的时间保持服务端原精度，避免仅修改名称时截断报名窗口秒数。
          const original = props.row?.[field.key]
          return [
            field.key,
            original && value === toLocalInput(original) ? original : new Date(value).toISOString(),
          ]
        }
        if (field.type === 'number') return [field.key, Number(value)]
        return [
          field.key,
          typeof value === 'string' && field.type !== 'password' ? value.trim() : value,
        ]
      }),
    ),
  )
}
</script>

<template>
  <FetchState :error="error" :pending="pending" @retry="reload">
    <form class="editor-form" @submit.prevent="submit">
      <p v-if="resource === 'users'" class="editor-hint muted">
        考点负责人需同时匹配授权考点和场次。清空任一范围会撤销考务数据访问；主办方角色对应空范围表示该维度可管理主办方全部资源。
      </p>
      <label
        v-for="field in shown"
        :key="field.key"
        :class="{ 'full-width': field.type === 'textarea' || field.type === 'multiselect' }"
      >
        {{ field.label }}
        <small v-if="field.hint" class="muted">{{ field.hint }}</small>
        <textarea
          v-if="field.type === 'textarea'"
          v-model="form[field.key]"
          rows="4"
          :required="!field.optional"
          :disabled="locked(field)"
          :maxlength="field.max"
        />
        <select
          v-else-if="field.type === 'select' || field.type === 'multiselect'"
          v-model="form[field.key]"
          :required="!field.optional"
          :disabled="locked(field)"
          :multiple="field.type === 'multiselect'"
          :size="field.type === 'multiselect' ? 3 : undefined"
        >
          <option v-if="field.type === 'select'" value="">请选择</option>
          <option v-for="option in choices(field)" :key="option.id" :value="option.id">
            {{ option.name }}
          </option>
        </select>
        <input
          v-else
          v-model="form[field.key]"
          :type="field.type"
          :required="!field.optional"
          :min="field.min"
          :disabled="locked(field)"
          :max="field.type === 'number' ? field.max : undefined"
          :step="field.step"
          :minlength="field.minLength"
          :maxlength="field.type === 'number' ? undefined : (field.max ?? 100)"
          :autocomplete="field.type === 'password' ? 'new-password' : 'off'"
        />
      </label>
      <div class="dialog-actions">
        <button type="button" class="button outline" :disabled="busy" @click="$emit('cancel')">
          取消
        </button>
        <button class="button primary" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </FetchState>
</template>
