<script setup>
import {
  Collection,
  User,
  EditPen,
  Printer,
  Search,
  Setting,
  Tickets,
} from '@element-plus/icons-vue'
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { candidateReturnQuery } from '@/domain/candidate-navigation.js'
import { auth, loadSession } from '@/composables/auth.js'
import { demoMode } from '@/api/client.js'

const route = useRoute()
const router = useRouter()
loadSession().catch(() => {})
watch(
  () => auth.expired,
  (expired) => {
    if (expired && route.meta.access === 'candidate') {
      router.replace({ name: 'login', query: candidateReturnQuery(route) })
    }
  },
)
const nav = [
  ['首页', '/'],
  ['考试项目', '/exams'],
  ['政策法规', '/notices?category=POLICY'],
  ['考务通知', '/notices?category=NOTICE'],
  ['报考指南', '/notices?category=GUIDE'],
  ['下载中心', '/downloads'],
  ['帮助中心', '/help'],
]
</script>

<template>
  <a class="skip-link" href="#main-content">跳至正文</a>
  <header class="portal-header">
    <div class="portal-width portal-top">
      <RouterLink class="brand" to="/"
        ><Collection aria-hidden="true" /><span>考试服务平台</span></RouterLink
      >
      <nav v-if="$route.meta.authPage" class="auth-nav" aria-label="公共导航">
        <RouterLink to="/">首页</RouterLink
        ><RouterLink to="/notices?category=NOTICE">考试公告</RouterLink>
        <RouterLink to="/notices?category=GUIDE">报考指南</RouterLink
        ><RouterLink to="/help">常见问题</RouterLink>
      </nav>
      <nav v-else class="top-actions" aria-label="服务入口">
        <RouterLink to="/services/registration"><Tickets aria-hidden="true" />在线报考</RouterLink>
        <RouterLink :to="auth.user?.roles.includes('CANDIDATE') ? '/account' : '/login'"
          ><User aria-hidden="true" />{{
            auth.user?.roles.includes('CANDIDATE') ? '我的账号' : '考生登录'
          }}</RouterLink
        >
        <RouterLink v-if="!auth.user?.roles.includes('CANDIDATE')" to="/register"
          ><EditPen aria-hidden="true" />考生注册</RouterLink
        >
        <RouterLink to="/services/ticket"><Printer aria-hidden="true" />准考证打印</RouterLink>
        <RouterLink to="/services/scores"><Search aria-hidden="true" />成绩查询</RouterLink>
        <RouterLink class="staff-link" to="/admin"
          ><Setting aria-hidden="true" />管理后台</RouterLink
        >
      </nav>
    </div>
  </header>
  <nav v-if="!$route.meta.authPage" class="portal-nav" aria-label="主导航">
    <div class="portal-width">
      <RouterLink
        v-for="[label, path] in nav"
        :key="path"
        :to="path"
        custom
        v-slot="{ href, navigate, route }"
      >
        <a
          :href="href"
          :aria-current="
            route.path === $route.path && route.query.category === $route.query.category
              ? 'page'
              : undefined
          "
          @click="navigate"
          >{{ label }}</a
        >
      </RouterLink>
    </div>
  </nav>
  <main id="main-content" :class="{ 'portal-main': !$route.meta.authPage }">
    <RouterView v-slot="{ Component }">
      <component
        :is="Component"
        :key="$route.name === 'exam-application' ? $route.params.id : undefined"
      />
    </RouterView>
  </main>
  <footer v-if="!$route.meta.authPage" class="portal-footer">
    <div class="portal-width">
      <nav aria-label="页脚">
        <RouterLink to="/sites">考点分布</RouterLink><RouterLink to="/help">使用帮助</RouterLink
        ><RouterLink to="/help#contact">联系我们</RouterLink>
      </nav>
      <span>{{ demoMode ? '本地演示 · 页面使用虚构数据' : '考试服务平台' }}</span>
    </div>
  </footer>
</template>
