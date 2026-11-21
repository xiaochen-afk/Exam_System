import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { expect, it, vi } from 'vitest'
import ExamApplication from '../ExamApplication.vue'
import { getApplicationOptions, submitApplication } from '@/api/candidate.js'

const exam = {
  id: 'first',
  name: '示例考试',
  subjectName: '示例科目',
  siteName: '示例考点',
  examStartsAt: '2026-12-05T01:00:00Z',
  examEndsAt: '2026-12-05T03:00:00Z',
  registrationStartsAt: '2026-11-01T00:00:00Z',
  registrationEndsAt: '2026-12-01T00:00:00Z',
  registrationStatus: 'OPEN',
  selectable: true,
}

vi.mock('@/api/client.js', () => ({
  request: vi.fn(async () => exam),
}))
vi.mock('@/api/candidate.js', () => ({
  getCandidateProfile: vi.fn(async () => ({
    candidateType: 'SOCIAL',
    schoolId: null,
    schoolName: null,
  })),
  getApplicationOptions: vi.fn(),
  submitApplication: vi.fn(),
}))

it('社会考生明确选点，收到满额后返回选择并保留可改选场次', async () => {
  const empty = { template: '<div></div>' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: empty },
      { path: '/exams', component: empty },
      { path: '/exams/:id/apply', name: 'exam-application', component: ExamApplication },
      { path: '/account/registrations', name: 'candidate-registrations', component: empty },
    ],
  })
  await router.push('/exams/first/apply')
  const alternate = { ...exam, id: 'second', siteName: '另一个考点' }
  getApplicationOptions
    .mockResolvedValueOnce({ items: [exam, alternate] })
    .mockResolvedValueOnce({
      items: [{ ...exam, registrationStatus: 'FULL', selectable: false }, alternate],
    })
  submitApplication.mockRejectedValueOnce(new Error('该场次已报满，请选择其他场次'))
  const wrapper = mount(ExamApplication, { props: { id: 'first' }, global: { plugins: [router] } })
  try {
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('button.button.primary').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).not.toContain('剩余名额')
    await wrapper.get('input[value=first]').setValue(true)
    await wrapper.get('button.button.primary').trigger('click')
    expect(wrapper.text()).toContain('核对报名信息')
    await wrapper.get('button.button.primary').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role=alert]').text()).toContain('已报满')
    expect(wrapper.get('input[value=first]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('input[value=second]').attributes('disabled')).toBeUndefined()
  } finally {
    wrapper.unmount()
  }
})
