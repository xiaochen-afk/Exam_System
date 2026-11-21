<script setup>
import { computed, ref, watch } from 'vue'
import { Calendar, ArrowRight } from '@element-plus/icons-vue'
import { request } from '@/api/client.js'
import { useAsync } from '@/composables/useAsync.js'
import { formatDate, noticeCategories } from '@/domain/presentation.js'
import FetchState from '@/components/FetchState.vue'
import ExamCard from '@/components/ExamCard.vue'
import campus from '@/assets/images/campus.jpg'

const category = ref('POLICY')
const {
  result: featured,
  pending: featurePending,
  error: featureError,
  reload: loadFeature,
} = useAsync(
  async (signal) =>
    (await request('/notices', { query: { featured: true, size: 1 }, signal })).items[0],
)
const {
  result: notices,
  pending: noticesPending,
  error: noticesError,
  reload: loadNotices,
} = useAsync((signal) =>
  request('/notices', { query: { category: category.value, size: 6 }, signal }),
)
const {
  result: exams,
  pending: examsPending,
  error: examsError,
  reload: loadExams,
} = useAsync((signal) => request('/exam-sessions', { query: { size: 4 }, signal }))
const moreNotices = computed(() => ({ path: '/notices', query: { category: category.value } }))
watch(category, loadNotices, { immediate: true })
loadFeature()
loadExams()
</script>

<template>
  <section class="portal-hero">
    <div class="portal-width">
      <h1>规范考试管理 · 服务考生需求</h1>
      <p><span>公平公正</span><span>规范有序</span><span>便捷高效</span></p>
    </div>
  </section>
  <div class="portal-width portal-home">
    <section aria-labelledby="news-title">
      <div class="section-heading">
        <h2 id="news-title">新闻与政策中心</h2>
        <RouterLink :to="moreNotices">更多资讯 <ArrowRight aria-hidden="true" /></RouterLink>
      </div>
      <div class="news-grid">
        <div class="featured panel">
          <FetchState
            :pending="featurePending"
            :error="featureError"
            :empty="!featured"
            empty-text="暂无焦点资讯"
            @retry="loadFeature"
          >
            <div class="featured-image">
              <img :src="campus" alt="校园建筑" />
              <span>焦点资讯</span>
            </div>
            <div class="featured-copy">
              <h3>
                <RouterLink :to="'/notices/' + featured.id">{{ featured.title }}</RouterLink>
              </h3>
              <p>{{ featured.summary }}</p>
              <div>
                <span><Calendar aria-hidden="true" /> {{ formatDate(featured.publishedAt) }}</span
                ><RouterLink :to="'/notices/' + featured.id"
                  >查看详情 <ArrowRight aria-hidden="true"
                /></RouterLink>
              </div>
            </div>
          </FetchState>
        </div>
        <div class="panel notice-panel">
          <div class="notice-tabs" role="group" aria-label="资讯分类">
            <button
              v-for="(label, key) in noticeCategories"
              :key="key"
              :aria-pressed="key === category"
              @click="category = key"
            >
              {{ label }}
            </button>
          </div>
          <FetchState
            :pending="noticesPending"
            :error="noticesError"
            :empty="!notices?.items.length"
            empty-text="该分类暂无资讯"
            @retry="loadNotices"
          >
            <ul class="notice-list">
              <li v-for="notice in notices.items" :key="notice.id">
                <RouterLink :to="'/notices/' + notice.id"
                  ><span v-if="notice.pinned" class="pinned">置顶</span
                  >{{ notice.title }}</RouterLink
                >
                <time :datetime="notice.publishedAt">{{ formatDate(notice.publishedAt) }}</time>
              </li>
            </ul>
          </FetchState>
        </div>
      </div>
    </section>
    <section aria-labelledby="exams-title">
      <div class="section-heading">
        <h2 id="exams-title">考试项目一览</h2>
        <RouterLink to="/exams">更多考试项目 <ArrowRight aria-hidden="true" /></RouterLink>
      </div>
      <FetchState
        :pending="examsPending"
        :error="examsError"
        :empty="!exams?.items.length"
        empty-text="暂无公开考试项目"
        @retry="loadExams"
      >
        <div class="exam-grid">
          <ExamCard
            v-for="(exam, index) in exams.items"
            :key="exam.id"
            :exam="exam"
            :index="index"
          />
        </div>
      </FetchState>
    </section>
  </div>
</template>
