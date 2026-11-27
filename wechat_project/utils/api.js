const { API_ORIGIN, DEVTOOLS_API_ORIGIN } = require('./api-config')
const session = require('./session')

class ApiError extends Error {
  constructor(code, message, requestId = '') {
    super(message)
    this.code = code
    this.requestId = requestId
  }
}

function origin() {
  const value = API_ORIGIN.trim().replace(/\/+$/, '')
  // 仅桌面开发者工具的开发版可以直接连接本机 HTTP 服务。
  if (!value && wx.getDeviceInfo().platform === 'devtools' &&
      wx.getAccountInfoSync().miniProgram.envVersion === 'develop') {
    return DEVTOOLS_API_ORIGIN
  }
  if (!/^https:\/\/[^/?#@\s]+$/.test(value)) {
    throw new ApiError('API_NOT_CONFIGURED', '考试服务地址尚未配置，请联系管理员')
  }
  return value
}

/** 统一封装平台请求与会话失效；页面只消费业务响应和受控错误。 */
async function request(path, { method = 'GET', data, authenticated = false } = {}) {
  const url = origin() + '/api/v1' + path
  const token = authenticated ? session.current() : ''
  if (authenticated && !token) {
    throw new ApiError('SESSION_REQUIRED', '请重新登录')
  }
  const header = { 'content-type': 'application/json' }
  if (token) header.Authorization = 'Bearer ' + token

  return new Promise((resolve, reject) => {
    wx.request({
      url,
      method,
      data,
      header,
      timeout: 12000,
      success({ statusCode, data: body }) {
        if (statusCode >= 200 && statusCode < 300) {
          resolve(body)
          return
        }

        if (statusCode === 401 && token && body && body.code === 'SESSION_REQUIRED') {
          session.clear(token)
        }
        const code = body && typeof body.code === 'string' ? body.code : 'REQUEST_FAILED'
        const message = body && typeof body.message === 'string' ? body.message : '服务暂时无法处理请求'
        const requestId = body && typeof body.requestId === 'string' ? body.requestId : ''
        reject(new ApiError(code, message, requestId))
      },
      fail() {
        reject(new ApiError('NETWORK_ERROR', '连接失败，请检查网络后重试'))
      }
    })
  })
}

module.exports = { ApiError, origin, request }
