const live = require('./mini-program-auth')
const session = require('./session')
const preview = require('./preview-data')
const { ApiError } = require('./api')
const { UI_PREVIEW } = require('./preview-config')

const demo = {
  async login() { return preview.signIn() },
  async link() { return preview.signIn() },
  async register() { return preview.signIn(false) },
  async me() { return preview.account() },
  async addWebCredential() { return preview.signIn() },
  async logout() { preview.signOut() }
}

function current() {
  return UI_PREVIEW ? preview.account() : session.current()
}

function requireAccount() {
  if (!current()) throw new ApiError('SESSION_REQUIRED', '请先登录考生账号')
}

module.exports = { ...(UI_PREVIEW ? demo : live), current, requireAccount }
