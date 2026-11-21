<script setup>
import { computed, ref, watch } from 'vue'
import { getSchools } from '@/api/candidate.js'
import { useAsync } from '@/composables/useAsync.js'
import FetchState from './FetchState.vue'

const props = defineProps({
  profile: { type: Object, required: true },
  busy: Boolean,
  error: String,
})
const emit = defineEmits(['continue'])
const candidateType = ref(props.profile.candidateType ?? '')
const schoolId = ref(props.profile.schoolId ?? '')
const schoolName = ref(props.profile.schoolName ?? '')
const input = ref('')
const query = ref({ page: 1, keyword: '' })
const {
  result,
  pending,
  error: schoolsError,
  reload,
} = useAsync((signal) => getSchools({ ...query.value, size: 20 }, signal))
const schools = computed(() => {
  const items = result.value?.items ?? []
  if (!schoolId.value || items.some(({ id }) => id === schoolId.value)) return items
  return [{ id: schoolId.value, name: schoolName.value }, ...items]
})
const canContinue = computed(
  () =>
    candidateType.value === 'SOCIAL' ||
    (candidateType.value === 'STUDENT' && Boolean(schoolId.value)),
)

watch(
  [candidateType, query],
  () => {
    if (candidateType.value === 'STUDENT') reload()
  },
  { immediate: true },
)

function chooseSchool(event) {
  const selection = schools.value.find(({ id }) => id === event.target.value)
  schoolId.value = selection?.id ?? ''
  schoolName.value = selection?.name ?? ''
}

function submit() {
  if (props.busy || !canContinue.value) return
  emit('continue', {
    candidateType: candidateType.value,
    schoolId: candidateType.value === 'STUDENT' ? schoolId.value : null,
  })
}
</script>

<template>
  <section class="application-section">
    <h2>确认考生资料</h2>
    <form class="candidate-form" @submit.prevent="submit">
      <fieldset :disabled="busy">
        <legend>考生类型</legend>
        <div class="inline-actions">
          <label class="choice"
            ><input
              v-model="candidateType"
              type="radio"
              value="SOCIAL"
              name="candidate-type"
            />社会考生</label
          >
          <label class="choice"
            ><input
              v-model="candidateType"
              type="radio"
              value="STUDENT"
              name="candidate-type"
            />在校考生</label
          >
        </div>
        <template v-if="candidateType === 'STUDENT'">
          <div class="school-search">
            <label
              >搜索在读学校<input
                v-model="input"
                type="search"
                maxlength="100"
                placeholder="输入学校名称"
                @keydown.enter.prevent="query = { page: 1, keyword: input.trim() }"
            /></label>
            <button
              type="button"
              class="button outline"
              @click="query = { page: 1, keyword: input.trim() }"
            >
              搜索
            </button>
          </div>
          <FetchState :pending="pending" :error="schoolsError" @retry="reload">
            <label
              >在读学校
              <select :value="schoolId" required @change="chooseSchool">
                <option value="">
                  {{ schools.length ? '请选择在读学校' : '未找到匹配的学校' }}
                </option>
                <option v-for="school in schools" :key="school.id" :value="school.id">
                  {{ school.name }}
                </option>
              </select>
            </label>
            <div v-if="result?.total > result?.size" class="inline-actions school-pages">
              <button
                type="button"
                class="button outline"
                :disabled="result.page <= 1"
                @click="query = { ...query, page: result.page - 1 }"
              >
                上一页
              </button>
              <span>第 {{ result.page }} 页</span>
              <button
                type="button"
                class="button outline"
                :disabled="result.page * result.size >= result.total"
                @click="query = { ...query, page: result.page + 1 }"
              >
                下一页
              </button>
            </div>
          </FetchState>
        </template>
      </fieldset>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <button class="button primary" :disabled="busy || !canContinue">
        {{ busy ? '保存中…' : '下一步，选择考点' }}
      </button>
    </form>
  </section>
</template>
