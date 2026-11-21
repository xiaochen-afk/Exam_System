import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

let request
beforeEach(async () => {
  vi.resetModules()
  request = (await import('../mock.js')).mockRequest
})
afterEach(() => vi.restoreAllMocks())

function login(identifier = 'candidate.demo', portal = 'CANDIDATE', password = 'Demo2026') {
  return request('/web-sessions', { method: 'POST', body: { identifier, password, portal } })
}

function saveProfile(candidateType, schoolId) {
  return request('/me/candidate-profile', { method: 'PUT', body: { candidateType, schoolId } })
}

function apply(sessionId = 'session-1', body = {}) {
  return request('/exam-sessions/' + sessionId + '/registrations', { method: 'POST', body })
}

function options(sessionId = 'session-1') {
  return request('/exam-sessions/' + sessionId + '/registration-options')
}

describe('Web 在线报考', () => {
  it('匿名和内部账号不能报考，资料及考点必须来自已发布场次和学校目录', async () => {
    await expect(options()).rejects.toMatchObject({ status: 401 })
    await login('organizer.demo', 'STAFF')
    await expect(apply()).rejects.toMatchObject({ status: 403 })
    await login()
    await expect(apply()).rejects.toMatchObject({ code: 'PROFILE_REQUIRED' })
    await expect(saveProfile('STUDENT', 'unverified-school')).rejects.toMatchObject({ status: 400 })
    await saveProfile('SOCIAL', null)
    await expect(
      apply('session-1', { candidateId: 'other', siteId: 'site-2' }),
    ).rejects.toMatchObject({ status: 400 })
    await expect(apply('session-5')).rejects.toMatchObject({ status: 404 })
  })

  it('在校考生看到本校推荐并可改选，报名记录归本人及实际考点', async () => {
    await login()
    await saveProfile('STUDENT', 'school-1')
    const { items } = await options()
    expect(items).toHaveLength(2)
    expect(items[0]).toMatchObject({ id: 'session-1', recommended: true, selectable: true })
    expect(items[1]).toMatchObject({ id: 'session-6', recommended: false, selectable: true })
    for (const option of items) {
      expect(option).not.toHaveProperty('capacity')
      expect(option).not.toHaveProperty('remainingCapacity')
    }
    const receipt = await apply('session-6')
    expect(receipt).toMatchObject({
      status: 'REGISTERED',
      sessionId: 'session-6',
      siteName: '西校区考点',
      submittedAt: expect.any(String),
    })
    await expect(apply('session-6')).rejects.toMatchObject({ code: 'ALREADY_REGISTERED' })
    await expect(apply('session-1')).rejects.toMatchObject({ code: 'ALREADY_REGISTERED' })
    await expect(request('/me/registrations')).resolves.toMatchObject({
      items: [receipt],
      total: 1,
    })
    expect((await options()).items.some((item) => item.selectable)).toBe(false)

    await request('/candidate-accounts', {
      method: 'POST',
      body: { identifier: 'other.demo', password: 'Example2026', displayName: '示例二' },
    })
    await login('other.demo', 'CANDIDATE', 'Example2026')
    await expect(
      request('/me/registrations', { query: { candidateId: 'candidate-1' } }),
    ).resolves.toMatchObject({ total: 0 })
    await login('organizer.demo', 'STAFF')
    const result = await request('/admin/registrations', {
      query: { sessionId: 'session-6', siteId: 'site-2' },
    })
    expect(result.items[0]).toMatchObject({
      id: receipt.id,
      candidateType: 'STUDENT',
      schoolName: '示例大学',
    })
    expect(result.items[0]).not.toHaveProperty('candidateId')
  })

  it('使用场次保存的起止时间，开始时允许提交，截止时拒绝提交', async () => {
    await login('organizer.demo', 'STAFF')
    const registrationStartsAt = '2026-12-01T00:00:00.000Z'
    const registrationEndsAt = '2026-12-02T00:00:00.000Z'
    await request('/admin/exam-sessions/session-1', {
      method: 'PATCH',
      body: {
        registrationStartsAt,
        registrationEndsAt,
        examStartsAt: '2026-12-05T01:00:00.000Z',
        examEndsAt: '2026-12-05T03:00:00.000Z',
      },
    })
    const clock = vi.spyOn(Date, 'now')
    clock.mockReturnValue(Date.parse(registrationStartsAt) - 1)
    const publicSession = await request('/exam-sessions/session-1')
    expect(publicSession).toMatchObject({
      registrationStartsAt,
      registrationEndsAt,
      registrationStatus: 'UPCOMING',
    })
    await login()
    await saveProfile('SOCIAL', null)
    await expect(apply()).rejects.toMatchObject({ code: 'REGISTRATION_UNAVAILABLE' })
    clock.mockReturnValue(Date.parse(registrationEndsAt))
    await expect(apply()).rejects.toMatchObject({ code: 'REGISTRATION_UNAVAILABLE' })
    clock.mockReturnValue(Date.parse(registrationStartsAt))
    await expect(apply()).resolves.toMatchObject({ sessionId: 'session-1' })
  })

  it('提交时重查场次容量，已满的本校考点不作推荐', async () => {
    await login()
    await saveProfile('STUDENT', 'school-1')
    expect((await options()).items[0].recommended).toBe(true)
    await login('organizer.demo', 'STAFF')
    await request('/admin/exam-sessions/session-1', { method: 'PATCH', body: { capacity: 16 } })
    await login()
    await expect(apply()).rejects.toMatchObject({ code: 'CAPACITY_FULL' })
    const { items } = await options()
    expect(items.find((item) => item.id === 'session-1')).toMatchObject({
      registrationStatus: 'FULL',
      selectable: false,
      recommended: false,
    })
    expect(items.find((item) => item.id === 'session-6')).toMatchObject({ selectable: true })
  })
})
