import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

let fetchMock
let api
beforeEach(async () => {
  vi.resetModules()
  vi.stubEnv('VITE_USE_MOCK', 'false')
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  api = await import('../client.js')
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('真实 HTTP 请求适配器', () => {
  it('写请求预先获取 CSRF，携带同源会话并保留错误信息', async () => {
    fetchMock
      .mockResolvedValueOnce(json({ token: 'test-token' }))
      .mockResolvedValueOnce(
        json({ code: 'IDENTIFIER_TAKEN', message: '账号已存在', requestId: 'req-test' }, 409),
      )
    await expect(
      api.request('/candidate-accounts', { method: 'POST', body: { identifier: 'test.user' } }),
    ).rejects.toMatchObject({ status: 409, code: 'IDENTIFIER_TAKEN', requestId: 'req-test' })
    expect(fetchMock.mock.calls.map(([url]) => url.pathname)).toEqual([
      '/api/v1/csrf-tokens',
      '/api/v1/candidate-accounts',
    ])
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'X-CSRF-TOKEN': 'test-token', 'Content-Type': 'application/json' },
    })
  })

  it('后台 401 清除会话，登录失败不触发会话失效通知', async () => {
    const handler = vi.fn()
    api.setUnauthorizedHandler(handler)
    fetchMock.mockResolvedValueOnce(json({ code: 'SESSION_REQUIRED', message: '会话已失效' }, 401))
    await expect(api.adminRequest('/dashboard')).rejects.toMatchObject({ status: 401 })
    fetchMock
      .mockResolvedValueOnce(json({ token: 'fresh-test-token' }))
      .mockResolvedValueOnce(json({ code: 'INVALID_CREDENTIALS', message: '账号或密码错误' }, 401))
    await expect(api.request('/web-sessions', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 401,
    })
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('对二进制下载保留响应体，对断网给出可重试消息', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('示例附件', { headers: { 'Content-Type': 'text/plain' } }),
    )
    const attachment = await api.request('/notices/a/attachments/b', { binary: true })
    expect(attachment.size).toBeGreaterThan(0)
    fetchMock.mockRejectedValueOnce(new TypeError('fetch failed'))
    await expect(api.request('/notices')).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  })
})
