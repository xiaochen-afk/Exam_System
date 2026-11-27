const { ApiError, origin, request } = require('./api')
const session = require('./session')

const ROOT = '/mini-program'

function requireCandidate(candidate) {
  if (!candidate || typeof candidate.id !== 'string' ||
      typeof candidate.displayName !== 'string' || typeof candidate.hasWebCredential !== 'boolean') {
    throw new ApiError('INVALID_RESPONSE', '账号响应不完整，请重试')
  }
  return candidate
}

function wechatCode() {
  return new Promise((resolve, reject) => {
    const fail = () => reject(new ApiError('WECHAT_LOGIN_FAILED', '无法获取微信登录凭证，请重试'))
    wx.login({
      success({ code }) {
        if (code) resolve(code)
        else fail()
      },
      fail
    })
  })
}

/** 每次提交都获取新的微信 code，服务端成功后才替换本地会话。 */
async function signIn(path, data = {}) {
  origin()
  const code = await wechatCode()
  const result = await request(ROOT + path, { method: 'POST', data: { ...data, code } })
  if (!result || typeof result.token !== 'string' || !result.token) {
    throw new ApiError('INVALID_RESPONSE', '登录响应不完整，请重试')
  }
  const candidate = requireCandidate(result.candidate)
  session.save(result.token)
  return candidate
}

const login = () => signIn('/sessions')
const link = (identifier, password) => signIn('/account-links', { identifier, password })
const register = (displayName) => signIn('/candidate-accounts', { displayName })
const me = () => request(ROOT + '/me', { authenticated: true }).then(requireCandidate)

function addWebCredential(identifier, password) {
  return request(ROOT + '/web-credentials', {
    method: 'POST',
    data: { identifier, password },
    authenticated: true
  }).then(requireCandidate)
}

async function logout() {
  const token = session.current()
  try {
    await request(ROOT + '/sessions/current', { method: 'DELETE', authenticated: true })
  } catch (error) {
    if (error.code !== 'SESSION_REQUIRED') throw error
  }
  session.clear(token)
}

module.exports = { login, link, register, me, addWebCredential, logout }
