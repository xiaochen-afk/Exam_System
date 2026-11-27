const KEY = 'exam-system-mini-session'
let token

/** 小程序会话是本地持久化的系统令牌，账号密码和微信 code 不进入缓存。 */
function current() {
  if (token === undefined) {
    try {
      const saved = wx.getStorageSync(KEY)
      token = typeof saved === 'string' ? saved : ''
    } catch {
      token = ''
    }
  }
  return token
}

function save(value) {
  token = value
  try {
    wx.setStorageSync(KEY, value)
  } catch {
    // 存储不可用时，仍可在当前小程序进程使用已签发的会话。
  }
}

function clear(expected = current()) {
  if (current() !== expected) return
  token = ''
  try {
    wx.removeStorageSync(KEY)
  } catch {
    // 服务端已失效的会话也无法在下一次打开时通过验证。
  }
}

module.exports = { current, save, clear }
