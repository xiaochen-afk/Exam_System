<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Reading, User, Expand, SwitchButton } from '@element-plus/icons-vue'
import { auth, hasPermission, signOut } from '@/composables/auth.js'
import { demoMode } from '@/api/client.js'
import { adminMenu } from '@/domain/admin.js'

const router = useRouter()
const menu = computed(() =>
  adminMenu.filter((entry) => !entry.permission || hasPermission(entry.permission)),
)
const expanded = ref(false)
const busy = ref(false)
const failure = ref('')
watch(
  () => auth.expired,
  (expired) => {
    if (expired) router.replace('/admin/login')
  },
)
watch(
  () => router.currentRoute.value.path,
  () => {
    expanded.value = false
  },
)

async function logout() {
  busy.value = true
  failure.value = ''
  try {
    await signOut()
    await router.replace('/admin/login')
  } catch (error) {
    failure.value = error.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <a class="skip-link" href="#admin-content">跳至正文</a>
  <div class="admin-shell">
    <header class="admin-top">
      <button
        class="icon-button menu-toggle"
        type="button"
        aria-label="切换菜单"
        :aria-expanded="expanded"
        aria-controls="admin-nav"
        @click="expanded = !expanded"
      >
        <Expand aria-hidden="true" />
      </button>
      <RouterLink class="brand admin-brand" to="/admin/dashboard"
        ><Reading aria-hidden="true" /><span>高校考试管理系统</span></RouterLink
      >
      <span class="admin-caption">考试管理 · 考务工作平台</span>
      <span v-if="demoMode" class="demo-indicator">本地演示</span>
      <div class="admin-account">
        <User aria-hidden="true" /><span>{{ auth.user?.displayName }}</span
        ><button
          :disabled="busy"
          class="icon-button"
          aria-label="退出登录"
          title="退出登录"
          @click="logout"
        >
          <SwitchButton aria-hidden="true" />
        </button>
      </div>
    </header>
    <aside :class="{ expanded }" class="admin-aside">
      <nav id="admin-nav" aria-label="后台功能">
        <RouterLink v-for="item in menu" :key="item.key" :to="'/admin/' + item.key"
          ><component :is="item.icon" aria-hidden="true" />{{ item.title }}</RouterLink
        >
      </nav>
      <RouterLink class="back-portal" to="/">返回门户</RouterLink>
    </aside>
    <main id="admin-content" class="admin-main">
      <p v-if="failure" class="inline-error" role="alert">{{ failure }}</p>
      <RouterView v-slot="{ Component }"
        ><component :is="Component" :key="$route.name"
      /></RouterView>
    </main>
  </div>
</template>
