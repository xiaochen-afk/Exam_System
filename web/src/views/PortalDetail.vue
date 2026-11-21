<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { request, downloadFile } from '@/api/client.js'
import { useAsync } from '@/composables/useAsync.js'
import { formatDate, noticeCategories } from '@/domain/presentation.js'
import StateBadge from '@/components/StateBadge.vue'
import FetchState from '@/components/FetchState.vue'

const route = useRoute()
const isExam = computed(() => route.name === 'exam-detail')
const resourcePath = computed(
  () => (isExam.value ? '/exam-sessions/' : '/notices/') + encodeURIComponent(route.params.id),
)
const {
  result: item,
  pending,
  error,
  reload,
} = useAsync((signal) => request(resourcePath.value, { signal }))
const activeDownload = ref('')
const downloadError = ref('')
watch(
  resourcePath,
  () => {
    downloadError.value = ''
    reload()
  },
  { immediate: true },
)

async function downloadAttachment(attachment) {
  activeDownload.value = attachment.id
  downloadError.value = ''
  try {
    const blob = await request(
      resourcePath.value + '/attachments/' + encodeURIComponent(attachment.id),
      { binary: true },
    )
    downloadFile(blob, attachment.filename)
  } catch (error) {
    downloadError.value = error.message
  } finally {
    activeDownload.value = ''
  }
}
</script>

<template>
  <div class="portal-width inner-page">
    <div class="breadcrumbs">
      <RouterLink to="/">首页</RouterLink><span>/</span
      ><RouterLink :to="isExam ? '/exams' : '/notices'">{{
        isExam ? '考试项目' : '新闻与政策中心'
      }}</RouterLink
      ><span>/</span>详情
    </div>
    <FetchState :error="error" :pending="pending" :empty="!item" @retry="reload">
      <article class="panel article">
        <header>
          <div class="article-category">
            {{ isExam ? '考试简章' : noticeCategories[item.category] }}
          </div>
          <h1>{{ isExam ? item.name : item.title }}</h1>
          <p v-if="!isExam" class="muted">
            {{ item.publisher }}　·　{{ formatDate(item.publishedAt) }}
          </p>
        </header>
        <dl v-if="isExam" class="detail-grid">
          <div>
            <dt>报名状态</dt>
            <dd><StateBadge :value="item.registrationStatus" /></dd>
          </div>
          <div>
            <dt>考试科目</dt>
            <dd>{{ item.subjectName }}</dd>
          </div>
          <div>
            <dt>考点</dt>
            <dd>{{ item.siteName }} · {{ item.siteAddress }}</dd>
          </div>
          <div>
            <dt>考试时间</dt>
            <dd>
              {{ formatDate(item.examStartsAt, true) }} 至 {{ formatDate(item.examEndsAt, true) }}
            </dd>
          </div>
          <div>
            <dt>报名时间</dt>
            <dd>
              {{ formatDate(item.registrationStartsAt, true) }} 至
              {{ formatDate(item.registrationEndsAt, true) }}
            </dd>
          </div>
        </dl>
        <p class="article-body">{{ isExam ? item.description : item.body }}</p>
        <section v-if="!isExam && item.attachments.length" class="attachments">
          <h2>附件下载</h2>
          <button
            v-for="file in item.attachments"
            :key="file.id"
            class="button outline"
            :disabled="!!activeDownload"
            @click="downloadAttachment(file)"
          >
            {{ file.filename }}
          </button>
          <p v-if="downloadError" class="error-text" role="alert">{{ downloadError }}</p>
        </section>
        <RouterLink
          v-if="isExam"
          class="button primary"
          :to="{ name: 'exam-application', params: { id: item.id } }"
          >{{ item.registrationStatus === 'OPEN' ? '立即报考' : '查看报考安排' }}</RouterLink
        >
      </article>
    </FetchState>
  </div>
</template>
