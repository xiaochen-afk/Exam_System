import { expect, it } from 'vitest'
import { staffPayload } from '../staff-authorization.js'

const options = {
  roles: [
    { id: 'SITE_MANAGER', scopes: [{ scopeType: 'SESSION', permissionCode: 'sessions.read' }] },
  ],
  projects: [],
  sessions: [{ id: '11' }, { id: '12' }],
}
const form = {
  identifier: 'test.staff',
  initialPassword: 'Fictional2026',
  displayName: '虚构测试人员',
  status: 'ACTIVE',
}
const grant = {
  roleCode: 'SITE_MANAGER',
  scopeType: 'SESSION',
  projectId: null,
  sessionId: '11',
  permissionCodes: ['sessions.read'],
}
it('拒绝重复范围和超出角色上限的动作', () => {
  expect(() => staffPayload({ form, grants: [grant, grant], options })).toThrow('不能重复')
  expect(() =>
    staffPayload({ form, grants: [{ ...grant, permissionCodes: ['admins.manage'] }], options }),
  ).toThrow('允许的动作')
})
it('保留两条独立范围，清除不适用的项目字段', () => {
  const payload = staffPayload({
    form,
    grants: [
      { ...grant, projectId: '999' },
      { ...grant, sessionId: '12' },
    ],
    options,
  })
  expect(payload.grants.map((item) => [item.projectId, item.sessionId])).toEqual([
    [null, '11'],
    [null, '12'],
  ])
})
