import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, expect, it, vi } from 'vitest'
import AuthView from '../AuthView.vue'
import { registerAccount, signIn, signOut } from '@/composables/auth.js'

vi.mock('@/composables/auth.js', () => ({
  auth: { user: null, expired: false },
  registerAccount: vi.fn(),
  signIn: vi.fn(),
  signOut: vi.fn(),
}))

let wrapper
afterEach(() => {
  wrapper?.unmount()
  vi.resetAllMocks()
})

async function open(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: AuthView },
      { path: '/register', name: 'register', component: AuthView },
      { path: '/account', name: 'account', component: { template: '<div>账号区</div>' } },
      { path: '/admin/login', name: 'admin-login', component: AuthView },
      { path: '/admin/dashboard', component: { template: '<div>工作台</div>' } },
    ],
  })
  await router.push(path)
  wrapper = mount(AuthView, { global: { plugins: [router] } })
  return router
}

it('注册时拒绝两次密码不一致，通过后返回登录页提示成功', async () => {
  const router = await open('/register')
  await wrapper.get('input[name=username]').setValue('qa.example')
  await wrapper.get('input[name=password]').setValue('Example2026')
  const confirmation = wrapper.get('input[placeholder="请再次输入密码"]')
  await confirmation.setValue('Mismatch2026')
  await wrapper.get('input[autocomplete=name]').setValue('测试考生')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role=alert]').text()).toContain('两次输入的密码不一致')
  expect(registerAccount).not.toHaveBeenCalled()

  await confirmation.setValue('Example2026')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(registerAccount).toHaveBeenCalledOnce()
  expect(router.currentRoute.value.path).toBe('/login')
  expect(wrapper.get('[role=status]').text()).toContain('注册成功')
})

it('正在登录时禁止重复提交，失败显示后端消息并恢复按钮', async () => {
  await open('/login')
  let rejectLogin
  signIn.mockImplementationOnce(
    () =>
      new Promise((resolve, reject) => {
        rejectLogin = reject
      }),
  )
  await wrapper.get('input[name=username]').setValue('qa.example')
  await wrapper.get('input[name=password]').setValue('Example2026')
  await wrapper.get('form').trigger('submit')
  await wrapper.get('form').trigger('submit')
  expect(signIn).toHaveBeenCalledExactlyOnceWith('qa.example', 'Example2026', 'CANDIDATE')
  expect(wrapper.get('button[type=submit], .auth-submit').attributes('disabled')).toBeDefined()
  rejectLogin(new Error('账号或密码错误'))
  await flushPromises()
  expect(wrapper.get('[role=alert]').text()).toContain('账号或密码错误')
  expect(wrapper.get('.auth-submit').attributes('disabled')).toBeUndefined()
})

it('没有后台授权时清理新会话并给出明确提示', async () => {
  const router = await open('/admin/login')
  signIn.mockResolvedValueOnce({ roles: [] })
  await wrapper.get('input[name=username]').setValue('qa.staff')
  await wrapper.get('input[name=password]').setValue('Example2026')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(signIn).toHaveBeenCalledExactlyOnceWith('qa.staff', 'Example2026', 'STAFF')
  expect(signOut).toHaveBeenCalledOnce()
  expect(router.currentRoute.value.path).toBe('/admin/login')
  expect(wrapper.get('[role=alert]').text()).toContain('账号尚未配置后台权限')
})

it.each(['ADMIN', 'EXAM_STAFF'])('%s 登录后正常进入工作台', async (role) => {
  const router = await open('/admin/login')
  signIn.mockResolvedValueOnce({ roles: [role] })
  await wrapper.get('input[name=username]').setValue('qa.staff')
  await wrapper.get('input[name=password]').setValue('Example2026')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(signOut).not.toHaveBeenCalled()
  expect(router.currentRoute.value.path).toBe('/admin/dashboard')
})
