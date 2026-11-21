<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { request } from '@/api/client.js'
import { useAsync } from '@/composables/useAsync.js'
import { formatDate, noticeCategories, states } from '@/domain/presentation.js'
import FetchState from '@/components/FetchState.vue'
import ExamCard from '@/components/ExamCard.vue'
import PageControl from '@/components/PageControl.vue'

const route = useRoute()
const router = useRouter()
const isExam = computed(() => route.name === 'exams')
const isDownload = computed(() => route.name === 'downloads')
const title = computed(() => {
  if (isExam.value) return '考试项目'
  if (isDownload.value) return '下载中心'
  return noticeCategories[route.query.category] ?? '新闻与政策中心'
})
const keyword = ref('')
const status = ref('')
const page = computed(() => Math.max(1, Number.parseInt(route.query.page, 10) || 1))
const { result, pending, error, reload } = useAsync((signal) =>
  request(isExam.value ? '/exam-sessions' : '/notices', {
    signal,
    query: {
      keyword: route.query.keyword,
      page: page.value,
      size: 12,
      category: isExam.value ? undefined : route.query.category,
      registrationStatus: isExam.value ? route.query.status : undefined,
      hasAttachments: isDownload.value ? true : undefined,
    },
  }),
)
watch(
  () => route.fullPath,
  () => {
    keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
    status.value = typeof route.query.status === 'string' ? route.query.status : ''
    reload()
  },
  { immediate: true },
)

function search() {
  router.push({
    query: {
      category: route.query.category,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
    },
  })
}
</script>

<template>
  <div class="portal-width inner-page">
    <div class="breadcrumbs"><RouterLink to="/">首页</RouterLink><span>/</span>{{ title }}</div>
    <section class="page-title">
      <h1>{{ title }}</h1>
    </section>
    <div v-if="!isExam && !isDownload" class="category-links" aria-label="资讯分类">
      <RouterLink :to="{ query: {} }" :data-active="!$route.query.category">全部资讯</RouterLink>
      <RouterLink
        v-for="(label, key) in noticeCategories"
        :key="key"
        :to="{ query: { category: key } }"
        :data-active="$route.query.category === key"
        >{{ label }}</RouterLink
      >
    </div>
    <form class="filter-bar panel portal-filter" @submit.prevent="search">
      <label
        >关键词<input
          v-model="keyword"
          maxlength="100"
          :placeholder="isExam ? '搜索考试或科目名称' : '搜索资讯标题'"
      /></label>
      <label v-if="isExam"
        >报名状态<select v-model="status">
          <option value="">全部状态</option>
          <option
            v-for="key in ['OPEN', 'UPCOMING', 'FULL', 'CLOSED', 'UNAVAILABLE']"
            :key="key"
            :value="key"
          >
            {{ states[key] }}
          </option>
        </select></label
      >
      <button class="button primary"><Search aria-hidden="true" />查询</button>
    </form>
    <FetchState :error="error" :pending="pending" :empty="!result?.items.length" @retry="reload">
      <div v-if="isExam" class="exam-grid">
        <ExamCard
          v-for="(exam, index) in result.items"
          :key="exam.id"
          :exam="exam"
          :index="index"
        />
      </div>
      <div v-else class="panel">
        <ul class="notice-list spacious">
          <li v-for="notice in result.items" :key="notice.id">
            <RouterLink :to="'/notices/' + notice.id"
              ><span v-if="notice.pinned" class="pinned">置顶</span>{{ notice.title }}</RouterLink
            ><time :datetime="notice.publishedAt">{{ formatDate(notice.publishedAt) }}</time>
          </li>
        </ul>
      </div>
    </FetchState>
    <PageControl
      v-if="!pending && !error && result"
      :page="page"
      :size="12"
      :total="result.total"
      @change="router.push({ query: { ...route.query, page: $event } })"
    />
  </div>
</template>
