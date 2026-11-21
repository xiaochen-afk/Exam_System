export class ApiError extends Error {
  constructor(status, code, message, requestId) {
    super(message)
    this.status = status
    this.code = code
    this.requestId = requestId
  }
}

export const demoMode = import.meta.env.DEV && import.meta.env.VITE_USE_MOCK !== 'false'
let csrfToken
let csrfRequest
let onUnauthenticated = () => {}

export function setUnauthorizedHandler(handler) {
  onUnauthenticated = handler
}

export function resetCsrf() {
  csrfToken = undefined
  csrfRequest = undefined
}

async function getCsrf() {
  if (csrfToken) return csrfToken
  if (!csrfRequest) {
    csrfRequest = request('/csrf-tokens')
      .then(({ token }) => {
        if (typeof token !== 'string' || !token) {
          throw new ApiError(502, 'UNEXPECTED_RESPONSE', '无法获取请求凭证')
        }
        csrfToken = token
        return token
      })
      .finally(() => {
        csrfRequest = undefined
      })
  }
  return csrfRequest
}

/** Calls the same relative API in both modes. Only the transport is simulated in local development. */
export async function request(
  path,
  { method = 'GET', query, body, session = false, binary = false, signal } = {},
) {
  const url = new URL('/api/v1' + path, globalThis.location.origin)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== '' && value != null) url.searchParams.set(key, value)
  }

  try {
    if (demoMode) {
      const { mockRequest } = await import('./mock.js')
      return await mockRequest(path, {
        method,
        query: Object.fromEntries(url.searchParams),
        body,
        signal,
      })
    }
    const headers = { Accept: binary ? '*/*' : 'application/json' }
    const options = { method, headers, signal, credentials: 'same-origin' }
    if (method !== 'GET') {
      headers['X-CSRF-TOKEN'] = await getCsrf()
      if (body instanceof FormData) {
        options.body = body
      } else if (body !== undefined) {
        headers['Content-Type'] = 'application/json'
        options.body = JSON.stringify(body)
      }
    }
    let response
    try {
      response = await fetch(url, options)
    } catch (error) {
      if (error.name === 'AbortError') throw error
      throw new ApiError(0, 'NETWORK_ERROR', '无法连接服务，请检查网络后重试')
    }
    if (!response.ok) {
      const failure = await response.json().catch(() => ({}))
      if (failure.code === 'CSRF_INVALID') resetCsrf()
      throw new ApiError(
        response.status,
        failure.code,
        failure.message || '请求失败，请稍后重试',
        failure.requestId,
      )
    }
    if (response.status === 204) return
    if (binary) return response.blob()
    if (!response.headers.get('content-type')?.includes('application/json')) {
      throw new ApiError(502, 'UNEXPECTED_RESPONSE', '服务返回了无法读取的数据')
    }
    return response.json()
  } catch (error) {
    if (session && error.status === 401 && !signal?.aborted) {
      resetCsrf()
      onUnauthenticated()
    }
    throw error
  }
}

export function adminRequest(path, options) {
  return request('/admin' + path, { ...options, session: true })
}

export function downloadFile(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
