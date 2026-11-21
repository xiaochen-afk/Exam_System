import { beforeEach, describe, expect, it, vi } from 'vitest'

let request
beforeEach(async () => {
  vi.resetModules()
  request = (await import('../mock.js')).mockRequest
})

function login(identifier, portal = 'STAFF', password = 'Demo2026') {
  return request('/web-sessions', { method: 'POST', body: { identifier, password, portal } })
}

describe('考生账号与内部权限', () => {
  it('匿名可注册；重复注册、错误入口及错误密码均被拒绝', async () => {
    const body = {
      identifier: 'qa.example',
      password: 'Example2026',
      displayName: '测试考生',
      roles: ['ADMIN'],
    }
    await request('/candidate-accounts', { method: 'POST', body })
    await expect(
      request('/candidate-accounts', {
        method: 'POST',
        body: { ...body, identifier: 'QA.example' },
      }),
    ).rejects.toMatchObject({ status: 409, code: 'IDENTIFIER_TAKEN' })
    await expect(login(body.identifier, 'STAFF', body.password)).rejects.toMatchObject({
      status: 401,
    })
    await expect(login(body.identifier, 'CANDIDATE', 'Wrong2026')).rejects.toMatchObject({
      status: 401,
    })
    await expect(login(body.identifier, 'CANDIDATE', body.password)).resolves.toMatchObject({
      roles: ['CANDIDATE'],
      permissions: [],
    })
    await expect(request('/admin/registrations')).rejects.toMatchObject({ status: 403 })
    await request('/web-sessions/current', { method: 'DELETE' })
    await expect(request('/me')).rejects.toMatchObject({ status: 401 })
    await expect(request('/admin/registrations')).rejects.toMatchObject({ status: 401 })
  })

  it('考点负责人只看到授权归属，管理员只能读账号等系统资源', async () => {
    await login('site.demo')
    const list = await request('/admin/registrations', { query: { size: 5 } })
    expect(list.items).toHaveLength(5)
    expect(list.total).toBe(16)
    expect(list.summary.total).toBe(16)
    expect(list.items.every((row) => row.siteId === 'site-1')).toBe(true)
    await expect(request('/admin/registrations/reg-0002')).rejects.toMatchObject({ status: 404 })
    await expect(
      request('/admin/registrations', { query: { siteId: 'site-2' } }),
    ).resolves.toMatchObject({ total: 0, summary: { total: 0 } })
    await expect(
      request('/admin/scores', { query: { sessionId: 'session-1' } }),
    ).rejects.toMatchObject({ status: 403 })
    await expect(
      request('/admin/sites/site-1', { method: 'PATCH', body: { status: 'INACTIVE' } }),
    ).rejects.toMatchObject({ status: 400 })
    await login('admin.demo')
    await expect(request('/admin/registrations')).rejects.toMatchObject({ status: 403 })
    await expect(request('/admin/users')).resolves.toMatchObject({ total: 3 })
    await request('/admin/users/staff-2', {
      method: 'PATCH',
      body: { siteIds: [], sessionIds: [] },
    })
    await expect(request('/admin/audit-logs')).resolves.toMatchObject({ total: 1 })
    await login('site.demo')
    await expect(request('/admin/registrations')).resolves.toMatchObject({ total: 0 })
  })
})

describe('场次、排考与成绩', () => {
  it('草稿场次不可公开，主办方发布后可以匿名查看', async () => {
    await expect(request('/exam-sessions/session-5')).rejects.toMatchObject({ status: 404 })
    await login('organizer.demo')
    await request('/admin/exam-sessions/session-5', {
      method: 'PATCH',
      body: { status: 'PUBLISHED' },
    })
    await request('/web-sessions/current', { method: 'DELETE' })
    const published = await request('/exam-sessions/session-5')
    expect(published.registrationStatus).toBe('UPCOMING')
    expect(published.registrationStartsAt).toBeTruthy()
    expect(published.registrationEndsAt).toBeTruthy()
    expect(published).not.toHaveProperty('capacity')
    expect(published).not.toHaveProperty('remainingCapacity')
  })

  it('排考拒绝重复座位，自动安排保留现有座位，开放后锁定', async () => {
    await login('organizer.demo')
    const assign = (id, seatNumber) =>
      request('/admin/registrations/' + id + '/arrangement', {
        method: 'PUT',
        body: { roomId: 'room-1-1', seatNumber },
      })
    const publish = () =>
      request('/admin/exam-sessions/session-1/ticket-publication', {
        method: 'PUT',
        body: { published: true },
      })
    await expect(publish()).rejects.toMatchObject({ code: 'ARRANGEMENT_INCOMPLETE' })
    await assign('reg-0001', 2)
    await expect(assign('reg-0003', 2)).rejects.toMatchObject({ code: 'SEAT_TAKEN' })
    await expect(
      request('/admin/exam-sessions/session-1/arrangement-runs', { method: 'POST', body: {} }),
    ).resolves.toMatchObject({ assigned: 15 })
    const list = await request('/admin/arrangements', {
      query: { sessionId: 'session-1', size: 50 },
    })
    expect(list.summary.assigned).toBe(16)
    expect(list.items.find((row) => row.id === 'reg-0001').seatNumber).toBe(2)
    expect(new Set(list.items.map((row) => row.roomId + ':' + row.seatNumber)).size).toBe(16)
    await expect(publish()).resolves.toEqual({ published: true })
    await expect(publish()).resolves.toEqual({ published: true })
    await expect(assign('reg-0001', 3)).rejects.toMatchObject({ code: 'STATE_CONFLICT' })
  })

  it('成绩支持 0 分，拒绝不完整发布并报告导入格式及重复行', async () => {
    await login('organizer.demo')
    const publish = () =>
      request('/admin/exam-sessions/session-1/score-publication', {
        method: 'PUT',
        body: { published: true },
      })
    const save = (score) =>
      request('/admin/registrations/reg-0001/score', { method: 'PUT', body: { score } })
    await expect(save(100.01)).rejects.toMatchObject({ code: 'VALIDATION_FAILED' })
    await save(0)
    await expect(publish()).rejects.toMatchObject({ code: 'SCORES_INCOMPLETE' })
    const before = await request('/admin/scores', { query: { sessionId: 'session-1' } })
    expect(before.items.find((row) => row.id === 'reg-0001')).toMatchObject({
      score: 0,
      scoreStatus: 'DRAFT',
    })
    const rows = Array.from(
      { length: 15 },
      (_, index) => 'BM-DEMO-' + String((index + 1) * 2 + 1).padStart(4, '0') + ',72.5',
    )
    const csv = ['registrationNumber,score', ...rows, 'BM-DEMO-0003,72', 'BM-DEMO-0002,72'].join(
      '\n',
    )
    const file = { name: 'scores.csv', size: csv.length, text: async () => csv }
    const report = await request('/admin/exam-sessions/session-1/score-imports', {
      method: 'POST',
      body: new Map([['file', file]]),
    })
    expect(report).toMatchObject({ applied: 15, failed: 2, errors: [{ row: 17 }, { row: 18 }] })
    await expect(publish()).resolves.toEqual({ published: true })
    await expect(save(1)).rejects.toMatchObject({ code: 'STATE_CONFLICT' })
  })
})
