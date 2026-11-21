<script setup>
import { ref, watch } from 'vue'
import { getMyRegistrations } from '@/api/candidate.js'
import { useAsync } from '@/composables/useAsync.js'
import { formatDate } from '@/domain/presentation.js'
import FetchState from '@/components/FetchState.vue'
import PageControl from '@/components/PageControl.vue'
import StateBadge from '@/components/StateBadge.vue'

const page = ref(1)
const size = 10
const { result, pending, error, reload } = useAsync((signal) =>
  getMyRegistrations({ page: page.value, size }, signal),
)
watch(page, reload, { immediate: true })
</script>

<template>
  <div class="portal-width inner-page application-page">
    <div class="breadcrumbs">
      <RouterLink to="/">首页</RouterLink><span>/</span
      ><RouterLink to="/account">考生账号</RouterLink><span>/</span>我的报名
    </div>
    <section class="page-title"><h1>我的报名</h1></section>
    <section class="panel application-panel">
      <FetchState :pending="pending" :error="error" @retry="reload">
        <p v-if="!result?.items.length" class="feedback">
          暂无报名记录
          <RouterLink :to="{ name: 'exams', query: { status: 'OPEN' } }" class="link-button"
            >查看可报考试</RouterLink
          >
        </p>
        <article v-for="item in result?.items" :key="item.id" class="candidate-receipt">
          <div class="receipt-header">
            <h2>
              <RouterLink :to="{ name: 'exam-detail', params: { id: item.sessionId } }">{{
                item.sessionName
              }}</RouterLink>
            </h2>
            <StateBadge :value="item.status" />
          </div>
          <dl class="detail-grid">
            <div>
              <dt>报名编号</dt>
              <dd>{{ item.registrationNumber }}</dd>
            </div>
            <div>
              <dt>考试科目</dt>
              <dd>{{ item.subjectName }}</dd>
            </div>
            <div>
              <dt>报考考点</dt>
              <dd>{{ item.siteName }}</dd>
            </div>
            <div>
              <dt>考试时间</dt>
              <dd>{{ formatDate(item.examStartsAt, true) }}</dd>
            </div>
            <div>
              <dt>报名提交时间</dt>
              <dd>{{ formatDate(item.submittedAt, true) }}</dd>
            </div>
          </dl>
        </article>
        <PageControl
          :page="page"
          :size="size"
          :total="result?.total ?? 0"
          @change="page = $event"
        />
      </FetchState>
    </section>
  </div>
</template>
