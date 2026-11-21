<script setup>
import { useRoute } from 'vue-router'
import { computed, watch } from 'vue'
import { useAsync } from '@/composables/useAsync.js'
import { request } from '@/api/client.js'
import FetchState from '@/components/FetchState.vue'

const route = useRoute()
const services = {
  ticket: [
    '准考证打印',
    '准考证开放后，请在考试服务微信小程序打开「我的报名」查看准考证，并按该场考试公告办理打印。',
  ],
  scores: ['成绩查询', '成绩发布后，请在考试服务微信小程序打开「我的报名」查询本人考试结果。'],
}
const service = computed(() => services[route.params.service])
const title = computed(() => service.value?.[0] ?? route.meta.title)
const { result, pending, error, reload } = useAsync((signal) =>
  request('/sites', { query: { page: Number(route.query.page) || 1, size: 20 }, signal }),
)

watch(
  () => [route.name, route.query.page],
  () => {
    if (route.name === 'sites') reload()
  },
  { immediate: true },
)
</script>

<template>
  <div class="portal-width inner-page">
    <div class="breadcrumbs"><RouterLink to="/">首页</RouterLink><span>/</span>{{ title }}</div>
    <section class="page-title">
      <h1>{{ title }}</h1>
    </section>
    <article v-if="service" class="panel article compact">
      <h2>办理方式</h2>
      <p>{{ service[1] }}</p>
      <p>微信小程序的正式入口以主办方发布的考务通知为准。</p>
      <RouterLink class="button outline" to="/notices?category=NOTICE">查看考务通知</RouterLink>
    </article>
    <template v-else-if="$route.name === 'sites'">
      <div class="panel article compact">
        <FetchState
          :error="error"
          :pending="pending"
          :empty="!result?.items.length"
          empty-text="暂无开放考点信息"
          @retry="reload"
        >
          <dl class="site-list">
            <div v-for="site in result.items" :key="site.id">
              <dt>{{ site.name }}</dt>
              <dd>{{ site.address }}</dd>
            </div>
          </dl>
          <div class="pager">
            <RouterLink
              v-if="result.page > 1"
              class="button outline"
              :to="{ query: { page: result.page - 1 } }"
              >上一页</RouterLink
            >
            <RouterLink
              v-if="result.page * result.size < result.total"
              class="button outline"
              :to="{ query: { page: result.page + 1 } }"
              >下一页</RouterLink
            >
          </div>
        </FetchState>
      </div>
    </template>
    <article v-else class="panel article compact">
      <h2>常见答疑</h2>
      <p>查看报考资格、考点选择及考试准备相关说明。</p>
      <RouterLink class="button outline" to="/notices?category=FAQ">浏览常见答疑</RouterLink>
      <h2 id="contact">考务咨询</h2>
      <p>咨询方式与受理时间见具体考试的考务通知。</p>
      <RouterLink class="button outline" to="/notices?category=NOTICE">查看考务通知</RouterLink>
    </article>
  </div>
</template>
