<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth, loadSession, signOut } from '@/composables/auth.js'

const router = useRouter()
const pending = ref(false)
const error = ref('')

async function verifySession() {
  try {
    const user = await loadSession({ refresh: true })
    if (router.currentRoute.value.name === 'account' && !user?.roles.includes('CANDIDATE')) {
      await router.replace('/login')
    }
  } catch (failure) {
    error.value = failure.message
  }
}

onMounted(() => window.addEventListener('focus', verifySession))
onUnmounted(() => window.removeEventListener('focus', verifySession))

async function logout() {
  pending.value = true
  error.value = ''
  try {
    await signOut()
    await router.replace('/login')
  } catch (failure) {
    error.value = failure.message
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="portal-width inner-page">
    <div class="breadcrumbs"><RouterLink to="/">首页</RouterLink><span>/</span>考生账号</div>
    <section class="page-title"><h1>考生账号</h1></section>
    <article class="panel article compact">
      <h2>{{ auth.user?.displayName }}</h2>
      <p class="success-text">已登录</p>
      <p>选择考试后在线报考，也可查看已提交的报名记录。</p>
      <div class="inline-actions">
        <RouterLink class="button primary" to="/services/registration">在线报考</RouterLink>
        <RouterLink class="button outline" :to="{ name: 'candidate-registrations' }"
          >我的报名</RouterLink
        ><button class="button outline" :disabled="pending" @click="logout">退出登录</button>
      </div>
      <p v-if="error" role="alert" class="error-text">{{ error }}</p>
    </article>
  </div>
</template>
