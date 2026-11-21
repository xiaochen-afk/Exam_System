<script setup>
import { candidateReturnRoute } from '@/domain/candidate-navigation.js'
</script>

<template>
  <div class="portal-width inner-page">
    <section class="panel article compact">
      <h1>{{ $route.meta.title }}</h1>
      <p v-if="$route.name === 'session-unavailable'">请检查网络连接后重试。</p>
      <p v-else-if="$route.name === 'admin-forbidden'">
        请选择左侧已授权的功能，或联系系统管理员。
      </p>
      <RouterLink
        class="button outline"
        :to="
          $route.name === 'admin-forbidden'
            ? '/admin/dashboard'
            : $route.name === 'session-unavailable'
              ? $route.query.section === 'staff'
                ? '/admin'
                : candidateReturnRoute($route.query)
              : '/'
        "
        >{{
          $route.name === 'admin-forbidden'
            ? '返回工作台'
            : $route.name === 'session-unavailable'
              ? '重新验证'
              : '返回首页'
        }}</RouterLink
      >
    </section>
  </div>
</template>
