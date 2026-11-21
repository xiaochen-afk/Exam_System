<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock, View, Hide, Setting } from '@element-plus/icons-vue'
import { demoMode } from '@/api/client.js'
import { auth, registerAccount, signIn, signOut } from '@/composables/auth.js'
import { isStaffUser, validateAccount } from '@/domain/account.js'
import { candidateReturnRoute } from '@/domain/candidate-navigation.js'

const route = useRoute()
const router = useRouter()
const staff = computed(() => route.name === 'admin-login')
const registering = computed(() => route.name === 'register')
const form = reactive({ identifier: '', password: '', confirmPassword: '', displayName: '' })
const visible = ref(false)
const busy = ref(false)
const failure = ref('')
const registered = ref(false)

watch(
  () => route.name,
  () => {
    form.password = ''
    form.confirmPassword = ''
    visible.value = false
    failure.value = ''
  },
)

async function submit() {
  if (busy.value) return
  failure.value = validateAccount(form, registering.value)
  if (failure.value) return
  registered.value = false
  const portal = staff.value ? 'STAFF' : 'CANDIDATE'
  const name = route.name
  const identifier = form.identifier.trim()
  busy.value = true
  try {
    if (registering.value) {
      await registerAccount({ ...form, identifier, displayName: form.displayName.trim() })
      if (route.name === name) {
        await router.replace({ name: 'login', query: route.query })
        registered.value = true
      }
    } else {
      const user = await signIn(identifier, form.password, portal)
      if (portal === 'STAFF' && !isStaffUser(user)) {
        // A valid staff credential can outlive its last grant. Do not leave an unusable session.
        await signOut()
        throw new Error('账号尚未配置后台权限，请联系管理员')
      }
      if (route.name === name)
        await router.replace(staff.value ? '/admin/dashboard' : candidateReturnRoute(route.query))
    }
  } catch (error) {
    if (route.name === name) failure.value = error.message
  } finally {
    busy.value = false
  }
}

function demoLogin(identifier) {
  if (!import.meta.env.DEV) return
  form.identifier = identifier
  form.password = 'Demo2026'
  submit()
}
</script>

<template>
  <div class="auth-scene" :class="{ 'auth-staff': staff }">
    <div class="portal-width auth-content">
      <div class="auth-intro">
        <p>{{ staff ? '考务工作平台' : '欢迎使用' }}</p>
        <h1>{{ staff ? '高校考试管理系统' : '考试服务平台' }}</h1>
        <div>
          {{
            staff
              ? '考试发布、报名管理、排考与成绩发布'
              : '浏览考试资讯、查阅报考指南，了解考试服务办理方式。'
          }}
        </div>
      </div>
      <section class="auth-card" :aria-label="staff ? '管理后台登录' : '考生账号'">
        <h2 v-if="staff">管理后台登录</h2>
        <nav v-else class="auth-tabs" aria-label="考生账号入口">
          <RouterLink :to="{ name: 'login', query: route.query }">考生登录</RouterLink
          ><RouterLink :to="{ name: 'register', query: route.query }">考生注册</RouterLink>
        </nav>
        <p v-if="staff" class="muted">仅限授权考务与系统管理人员登录。</p>
        <p v-if="registered && !registering" role="status" class="success-text">
          注册成功，请使用新账号登录。
        </p>
        <p v-if="auth.expired" role="status" class="muted">会话已失效，请重新登录。</p>
        <p v-if="demoMode && registering" class="muted">本地演示，请使用虚构账号信息。</p>
        <form class="auth-form" @submit.prevent="submit">
          <label
            >{{ registering ? '注册账号' : '登录账号' }}
            <span class="input-icon"
              ><User aria-hidden="true" /><input
                v-model="form.identifier"
                name="username"
                autocomplete="username"
                :maxlength="registering ? 32 : 128"
                :placeholder="registering ? '设置 4–32 位账号' : '请输入登录账号'"
                required
            /></span>
          </label>
          <label
            >{{ registering ? '设置密码' : '登录密码' }}
            <span class="input-icon"
              ><Lock aria-hidden="true" /><input
                v-model="form.password"
                name="password"
                :type="visible ? 'text' : 'password'"
                :autocomplete="registering ? 'new-password' : 'current-password'"
                :maxlength="registering ? 64 : 256"
                placeholder="请输入密码"
                required
              />
              <button
                type="button"
                class="icon-button"
                :aria-label="visible ? '隐藏密码' : '显示密码'"
                :aria-pressed="visible"
                @click="visible = !visible"
              >
                <component :is="visible ? Hide : View" aria-hidden="true" />
              </button>
            </span>
          </label>
          <template v-if="registering">
            <label
              >确认密码<input
                v-model="form.confirmPassword"
                type="password"
                autocomplete="new-password"
                maxlength="64"
                placeholder="请再次输入密码"
                required
            /></label>
            <label
              >姓名<input
                v-model="form.displayName"
                autocomplete="name"
                maxlength="50"
                placeholder="请输入姓名"
                required
            /></label>
          </template>
          <p v-if="failure" role="alert" class="error-text">{{ failure }}</p>
          <button class="button primary auth-submit" :disabled="busy">
            {{ busy ? '正在提交…' : registering ? '注册' : '登录' }}
          </button>
        </form>
        <div class="auth-links">
          <RouterLink v-if="staff" :to="{ name: 'login', query: route.query }"
            >返回考生登录</RouterLink
          >
          <RouterLink v-else-if="registering" :to="{ name: 'login', query: route.query }"
            >已有账号，前往登录</RouterLink
          >
          <span v-else
            >还没有账号？
            <RouterLink :to="{ name: 'register', query: route.query }">立即注册</RouterLink></span
          >
          <RouterLink v-if="!staff" to="/admin/login"
            ><Setting aria-hidden="true" />管理后台</RouterLink
          >
        </div>
        <div v-if="demoMode && !registering" class="demo-links">
          <span>本地演示</span>
          <template v-if="staff">
            <button type="button" :disabled="busy" @click="demoLogin('organizer.demo')">
              主办方考务
            </button>
            <button type="button" :disabled="busy" @click="demoLogin('site.demo')">
              考点负责人
            </button>
            <button type="button" :disabled="busy" @click="demoLogin('admin.demo')">
              系统管理员
            </button>
          </template>
          <button v-else type="button" :disabled="busy" @click="demoLogin('candidate.demo')">
            体验考生账号
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
