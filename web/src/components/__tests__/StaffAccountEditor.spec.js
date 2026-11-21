import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import StaffAccountEditor from '../StaffAccountEditor.vue'
import { adminRequest } from '@/api/client.js'

vi.mock('@/api/client.js', () => ({ adminRequest: vi.fn() }))
const options = {
  roles: [
    {
      id: 'SITE_MANAGER',
      name: '考点负责人',
      scopes: [
        { scopeType: 'SESSION', permissionCode: 'sessions.read', permissionLabel: '查看场次' },
      ],
    },
  ],
  projects: [],
  sessions: [
    { id: '11', name: '虚构甲场次' },
    { id: '12', name: '虚构乙场次' },
  ],
}
let wrapper
afterEach(() => {
  wrapper?.unmount()
  vi.resetAllMocks()
})
async function open(row) {
  adminRequest.mockResolvedValue(structuredClone(options))
  wrapper = mount(StaffAccountEditor, { props: { row } })
  await flushPromises()
}

it('新增账号发送逐条动作与范围，不发送旧授权字段', async () => {
  await open()
  await wrapper.get('input[autocomplete=off]').setValue('qa.new.staff')
  await wrapper.findAll('input[autocomplete=off]')[1].setValue('虚构联调人员')
  await wrapper.get('input[type=password]').setValue('Fictional2026')
  await wrapper
    .findAll('button')
    .find((button) => button.text() === '添加授权')
    .trigger('click')
  await wrapper.get('select[aria-label="授权 1 考试场次"]').setValue('11')
  await wrapper.get('input[value="sessions.read"]').setValue(true)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('save')[0][0]).toEqual({
    identifier: 'qa.new.staff',
    initialPassword: 'Fictional2026',
    displayName: '虚构联调人员',
    status: 'ACTIVE',
    grants: [
      {
        roleCode: 'SITE_MANAGER',
        scopeType: 'SESSION',
        projectId: null,
        sessionId: '11',
        permissionCodes: ['sessions.read'],
      },
    ],
  })
})

it('仅修改资料时省略 grants，明确清空授权时发送空数组', async () => {
  await open({
    id: '4',
    displayName: '虚构已有人员',
    status: 'ACTIVE',
    grants: [
      {
        roleCode: 'SITE_MANAGER',
        scopeType: 'SESSION',
        projectId: null,
        sessionId: '11',
        permissionCodes: ['sessions.read'],
      },
    ],
  })
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('save')[0][0]).toEqual({ displayName: '虚构已有人员', status: 'ACTIVE' })
  await wrapper.get('input[type=checkbox]').setValue(true)
  await wrapper.get('button[aria-label="删除授权 1"]').trigger('click')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('save')[1][0].grants).toEqual([])
})

it('保护不经 HTTP 委派的管理员动作', async () => {
  await open({
    id: '5',
    displayName: '虚构管理员',
    status: 'ACTIVE',
    grants: [
      {
        roleCode: 'SYSTEM_ADMIN',
        scopeType: 'SYSTEM',
        permissionCodes: ['admins.manage', 'users.write'],
      },
    ],
  })
  expect(wrapper.text()).toContain('受保护的管理员权限')
  expect(wrapper.find('input[type=checkbox]').exists()).toBe(false)
  await wrapper.get('form').trigger('submit')
  expect(wrapper.emitted('save')[0][0]).not.toHaveProperty('grants')
})
