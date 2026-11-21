<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import CandidateProfileForm from '@/components/CandidateProfileForm.vue'
import FetchState from '@/components/FetchState.vue'
import StateBadge from '@/components/StateBadge.vue'
import { request } from '@/api/client.js'
import {
  getApplicationOptions,
  getCandidateProfile,
  saveCandidateProfile,
  submitApplication,
} from '@/api/candidate.js'
import { useAction } from '@/composables/useAction.js'
import { useAsync } from '@/composables/useAsync.js'
import { formatDate, states } from '@/domain/presentation.js'

const props = defineProps({ id: { type: String, required: true } })
const route = useRoute()
const stage = ref(1)
const selectedId = ref('')
const profile = ref()
const receipt = ref()
const heading = ref()
const { busy, failure, output, run } = useAction()
const {
  result: initial,
  pending: initialPending,
  error: initialError,
  reload: reloadInitial,
} = useAsync(async (signal) => {
  const [exam, candidateProfile] = await Promise.all([
    request('/exam-sessions/' + encodeURIComponent(props.id), { signal }),
    getCandidateProfile(signal),
  ])
  profile.value = candidateProfile
  return exam
})
const {
  result: choices,
  pending: choicesPending,
  error: choicesError,
  reload: reloadChoices,
} = useAsync((signal) => getApplicationOptions(props.id, signal))
const steps = ['确认考生资料', '选择考点', '核对并提交']
const selected = computed(() => choices.value?.items.find((item) => item.id === selectedId.value))
reloadInitial()

watch(stage, async () => {
  await nextTick()
  heading.value?.focus()
})

watch(choices, (value) => {
  if (stage.value === 2 && !selectedId.value) {
    selectedId.value = value?.items.find((item) => item.recommended && item.selectable)?.id ?? ''
  }
})

async function saveProfile(form) {
  failure.value = ''
  if (
    profile.value.candidateType !== form.candidateType ||
    profile.value.schoolId !== form.schoolId
  ) {
    if (!(await run(() => saveCandidateProfile(form)))) return
    profile.value = output.value
  }
  selectedId.value = ''
  stage.value = 2
  await reloadChoices()
}

function back() {
  failure.value = ''
  stage.value -= 1
}

async function submit() {
  if (busy.value || !selected.value?.selectable) return
  if (await run(() => submitApplication(selectedId.value))) {
    if (route.name === 'exam-application' && route.params.id === props.id) {
      receipt.value = output.value
      stage.value = 4
    }
    return
  }
  // A site can close or fill between selection and submission. Keep the choice visible for review.
  stage.value = 2
  await reloadChoices()
}
</script>

<template>
  <div class="portal-width inner-page application-page">
    <div class="breadcrumbs">
      <RouterLink to="/">首页</RouterLink><span>/</span><RouterLink to="/exams">考试项目</RouterLink
      ><span>/</span>在线报考
    </div>
    <section class="page-title"><h1>在线报考</h1></section>
    <FetchState
      :pending="initialPending"
      :error="initialError"
      :empty="!initial"
      @retry="reloadInitial"
    >
      <div class="panel application-panel">
        <div class="application-exam">
          <strong>{{ initial.name }}</strong
          ><span>{{ initial.subjectName }}</span>
        </div>
        <ol v-if="!receipt" class="application-steps" aria-label="报考进度">
          <li
            v-for="(label, index) in steps"
            :key="label"
            :aria-current="stage === index + 1 ? 'step' : undefined"
            :data-complete="index + 1 < stage"
          >
            <span>{{ index + 1 }}</span
            >{{ label }}
          </li>
        </ol>
        <div ref="heading" tabindex="-1" class="application-content">
          <section v-if="receipt" class="application-section">
            <h2 class="success-text" role="status">报名成功</h2>
            <dl class="detail-grid">
              <div>
                <dt>报名编号</dt>
                <dd>{{ receipt.registrationNumber }}</dd>
              </div>
              <div>
                <dt>报考考点</dt>
                <dd>{{ receipt.siteName }}</dd>
              </div>
              <div>
                <dt>报名提交时间</dt>
                <dd>{{ formatDate(receipt.submittedAt, true) }}</dd>
              </div>
              <div>
                <dt>考试时间</dt>
                <dd>{{ formatDate(receipt.examStartsAt, true) }}</dd>
              </div>
            </dl>
            <RouterLink class="button primary" :to="{ name: 'candidate-registrations' }"
              >查看我的报名</RouterLink
            >
          </section>
          <CandidateProfileForm
            v-else-if="stage === 1"
            :profile="profile"
            :busy="busy"
            :error="failure"
            @continue="saveProfile"
          />
          <section v-else-if="stage === 2" class="application-section">
            <h2>选择考点</h2>
            <p v-if="failure" class="inline-error" role="alert">{{ failure }}</p>
            <FetchState
              :pending="choicesPending"
              :error="choicesError"
              :empty="!choices?.items.length"
              empty-text="暂无场次可选"
              @retry="reloadChoices"
            >
              <p v-if="!choices.items.some((item) => item.selectable)" class="muted">
                该科目暂时没有可提交的场次，请查看各考点状态。
              </p>
              <p
                v-else-if="
                  profile.candidateType === 'STUDENT' &&
                  !choices.items.some((item) => item.recommended)
                "
                class="muted"
              >
                暂无可报名的本校考点，请选择其他开放考点。
              </p>
              <fieldset class="application-options">
                <legend class="sr-only">报考考点及场次</legend>
                <label
                  v-for="item in choices.items"
                  :key="item.id"
                  class="application-option"
                  :data-selected="selectedId === item.id"
                >
                  <input
                    v-model="selectedId"
                    name="exam-session"
                    type="radio"
                    :value="item.id"
                    :disabled="!item.selectable"
                  />
                  <span class="option-main">
                    <span class="option-title"
                      ><strong>{{ item.siteName }}</strong
                      ><span v-if="item.recommended" class="recommendation">本校推荐</span></span
                    >
                    <span>{{ item.siteAddress }}</span>
                    <span>考试时间　{{ formatDate(item.examStartsAt, true) }}</span>
                    <span
                      >报名开始　{{ formatDate(item.registrationStartsAt, true) }}<br />报名截止　{{
                        formatDate(item.registrationEndsAt, true)
                      }}</span
                    >
                    <span v-if="item.unavailableReason && !item.registration" class="muted">{{
                      item.unavailableReason
                    }}</span>
                    <RouterLink v-if="item.registration" :to="{ name: 'candidate-registrations' }"
                      >已报名 · {{ item.registration.registrationNumber }}</RouterLink
                    >
                  </span>
                  <StateBadge :value="item.registration?.status ?? item.registrationStatus" />
                </label>
              </fieldset>
            </FetchState>
            <div class="inline-actions">
              <button type="button" class="button outline" @click="back">上一步</button>
              <button
                type="button"
                class="button primary"
                :disabled="choicesPending || Boolean(choicesError) || !selected?.selectable"
                @click="stage = 3"
              >
                下一步，核对信息
              </button>
            </div>
          </section>
          <section v-else class="application-section">
            <h2>核对报名信息</h2>
            <dl class="detail-grid">
              <div>
                <dt>考试科目</dt>
                <dd>{{ selected.subjectName }}</dd>
              </div>
              <div>
                <dt>考生类型</dt>
                <dd>
                  {{ states[profile.candidateType]
                  }}<template v-if="profile.schoolName"> · {{ profile.schoolName }}</template>
                </dd>
              </div>
              <div>
                <dt>报考考点</dt>
                <dd>{{ selected.siteName }}</dd>
              </div>
              <div>
                <dt>考试时间</dt>
                <dd>
                  {{ formatDate(selected.examStartsAt, true) }} 至
                  {{ formatDate(selected.examEndsAt, true) }}
                </dd>
              </div>
              <div>
                <dt>报名开始</dt>
                <dd>{{ formatDate(selected.registrationStartsAt, true) }}</dd>
              </div>
              <div>
                <dt>报名截止</dt>
                <dd>{{ formatDate(selected.registrationEndsAt, true) }}</dd>
              </div>
            </dl>
            <div class="inline-actions">
              <button type="button" class="button outline" :disabled="busy" @click="back">
                返回选择
              </button>
              <button type="button" class="button primary" :disabled="busy" @click="submit">
                {{ busy ? '提交中…' : '确认提交报名' }}
              </button>
            </div>
          </section>
        </div>
      </div>
    </FetchState>
  </div>
</template>
