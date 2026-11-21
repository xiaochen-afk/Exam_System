<script setup>
import { computed } from 'vue'
import AdminTitle from '@/components/AdminTitle.vue'
import FetchState from '@/components/FetchState.vue'
import { adminRequest } from '@/api/client.js'
import { hasPermission } from '@/composables/auth.js'
import { useAsync } from '@/composables/useAsync.js'
import { adminMenu } from '@/domain/admin.js'

const entries = computed(() =>
  adminMenu.filter(({ permission }) => permission && hasPermission(permission)),
)
const { result, pending, error, reload } = useAsync((signal) =>
  adminRequest('/dashboard', { signal }),
)
reload()
const metrics = {
  sessions: '考试场次',
  registrations: '报名总人数',
  assigned: '已排考',
  users: '内部账号',
}
</script>

<template>
  <AdminTitle title="工作台" summary="查看当前授权范围内的工作概况" />
  <section class="panel admin-panel">
    <h2>工作概览</h2>
    <FetchState
      :error="error"
      :pending="pending"
      :empty="!result?.metrics.length"
      empty-text="当前账号暂无可查看的统计项目"
      @retry="reload"
    >
      <div class="metrics-grid">
        <article v-for="(item, index) in result.metrics" :key="item.key" :data-color="index % 4">
          <span>{{ metrics[item.key] }}</span
          ><strong>{{ item.value.toLocaleString('zh-CN') }}</strong>
        </article>
      </div>
    </FetchState>
  </section>
  <section class="panel admin-panel">
    <h2>工作入口</h2>
    <p v-if="!entries.length" class="muted">当前账号暂无已授权功能。</p>
    <div class="dashboard-links">
      <RouterLink v-for="item in entries" :key="item.key" :to="'/admin/' + item.key"
        ><component :is="item.icon" aria-hidden="true" />{{ item.title }}</RouterLink
      >
    </div>
  </section>
</template>
