import { reactive } from 'vue'
import { request, resetCsrf, setUnauthorizedHandler } from '@/api/client.js'

export const auth = reactive({ user: null, known: false, expired: false })
let revision = 0
let loading

function replaceSession(user, expired = false) {
  revision += 1
  auth.user = user
  auth.known = true
  auth.expired = expired
}

setUnauthorizedHandler(() => replaceSession(null, true))

export function hasPermission(permission) {
  return auth.user?.permissions.includes(permission) ?? false
}

export async function loadSession({ refresh = false } = {}) {
  if (auth.known && !refresh) return auth.user
  if (!loading) {
    const currentRevision = revision
    loading = (async () => {
      try {
        const user = await request('/me')
        if (currentRevision === revision) replaceSession(user)
      } catch (error) {
        if (error.status !== 401) throw error
        if (currentRevision === revision) replaceSession(null, auth.user !== null || auth.expired)
      }
      return auth.user
    })().finally(() => {
      loading = null
    })
  }
  return loading
}

export async function signIn(identifier, password, portal) {
  const user = await request('/web-sessions', {
    method: 'POST',
    body: { identifier, password, portal },
  })
  resetCsrf()
  replaceSession(user)
  return user
}

export function registerAccount({ identifier, password, displayName }) {
  return request('/candidate-accounts', {
    method: 'POST',
    body: { identifier, password, displayName },
  })
}

export async function signOut() {
  try {
    await request('/web-sessions/current', { method: 'DELETE' })
  } catch (error) {
    if (error.status !== 401) throw error
  }
  resetCsrf()
  replaceSession(null)
}
