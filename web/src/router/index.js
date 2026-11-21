import { createRouter, createWebHistory } from 'vue-router'
import { auth, hasPermission, loadSession } from '@/composables/auth.js'
import PortalLayout from '@/layouts/PortalLayout.vue'
import PortalHome from '@/views/PortalHome.vue'
import { adminMenu } from '@/domain/admin.js'
import { candidateReturnQuery } from '@/domain/candidate-navigation.js'
import { isStaffUser } from '@/domain/account.js'

const authView = () => import('@/views/AuthView.vue')
const listView = () => import('@/views/PortalList.vue')
const detailView = () => import('@/views/PortalDetail.vue')
const infoView = () => import('@/views/PortalInfo.vue')
const adminViews = {
  dashboard: () => import('@/views/admin/DashboardView.vue'),
  notices: () => import('@/views/admin/NoticesView.vue'),
  registrations: () => import('@/views/admin/RegistrationsView.vue'),
  arrangements: () => import('@/views/admin/ArrangementsView.vue'),
  scores: () => import('@/views/admin/ScoresView.vue'),
  roles: () => import('@/views/admin/RolesView.vue'),
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(to, from, saved) {
    if (to.hash) return { el: to.hash }
    if (saved) return saved
    if (to.path !== from.path) return { top: 0 }
  },
  routes: [
    {
      path: '/',
      component: PortalLayout,
      children: [
        { path: '', name: 'home', component: PortalHome },
        {
          path: 'login',
          name: 'login',
          component: authView,
          meta: { authPage: true, title: '考生登录' },
        },
        {
          path: 'register',
          name: 'register',
          component: authView,
          meta: { authPage: true, title: '考生注册' },
        },
        {
          path: 'admin/login',
          name: 'admin-login',
          component: authView,
          meta: { authPage: true, title: '管理后台登录' },
        },
        {
          path: 'account',
          name: 'account',
          component: () => import('@/views/AccountView.vue'),
          meta: { access: 'candidate', title: '考生账号' },
        },
        {
          path: 'account/registrations',
          name: 'candidate-registrations',
          component: () => import('@/views/CandidateRegistrations.vue'),
          meta: { access: 'candidate', title: '我的报名' },
        },
        { path: 'exams', name: 'exams', component: listView, meta: { title: '考试项目' } },
        {
          path: 'exams/:id/apply',
          name: 'exam-application',
          props: true,
          component: () => import('@/views/ExamApplication.vue'),
          meta: { access: 'candidate', title: '在线报考' },
        },
        {
          path: 'exams/:id',
          name: 'exam-detail',
          component: detailView,
          meta: { title: '考试简章' },
        },
        {
          path: 'notices',
          name: 'notices',
          component: listView,
          meta: { title: '新闻与政策中心' },
        },
        {
          path: 'notices/:id',
          name: 'notice-detail',
          component: detailView,
          meta: { title: '资讯详情' },
        },
        { path: 'downloads', name: 'downloads', component: listView, meta: { title: '下载中心' } },
        { path: 'help', name: 'help', component: infoView, meta: { title: '帮助中心' } },
        { path: 'sites', name: 'sites', component: infoView, meta: { title: '考点分布' } },
        { path: 'services/registration', redirect: { name: 'exams', query: { status: 'OPEN' } } },
        {
          path: 'services/:service(ticket|scores)',
          name: 'service',
          component: infoView,
          meta: { title: '办理指南' },
        },
        {
          path: 'session-unavailable',
          name: 'session-unavailable',
          component: () => import('@/views/StatusView.vue'),
          meta: { title: '暂时无法验证会话' },
        },
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: () => import('@/views/StatusView.vue'),
          meta: { title: '页面不存在' },
        },
      ],
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { access: 'staff' },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        ...adminMenu.map(({ key, title, permission }) => ({
          path: key,
          name: 'admin-' + key,
          component: adminViews[key] ?? (() => import('@/views/admin/ResourceView.vue')),
          meta: { title, permission, resource: key },
        })),
        {
          path: 'forbidden',
          name: 'admin-forbidden',
          component: () => import('@/views/StatusView.vue'),
          meta: { title: '无权访问该页面' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.access) return
  try {
    await loadSession({ refresh: true })
  } catch {
    return {
      name: 'session-unavailable',
      query: { section: to.meta.access, ...candidateReturnQuery(to) },
    }
  }
  if (to.meta.access === 'candidate') {
    return (
      auth.user?.roles.includes('CANDIDATE') || { name: 'login', query: candidateReturnQuery(to) }
    )
  }
  if (!isStaffUser(auth.user)) return { name: 'admin-login' }
  if (to.meta.permission && !hasPermission(to.meta.permission)) return { name: 'admin-forbidden' }
})

router.afterEach((to) => {
  document.title = to.meta.title ? to.meta.title + ' · 考试服务平台' : '考试服务平台'
})

export default router
