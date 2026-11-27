const auth = require('../../utils/account')
const { UI_PREVIEW } = require('../../utils/preview-config')

Page({
  data: {
    preview: UI_PREVIEW,
    screen: 'login',
    pending: '',
    error: '',
    requestId: '',
    candidate: null,
    displayName: '',
    identifier: '',
    password: ''
  },

  onLoad(options) {
    this.returnAfterSignIn = options.return === '1'
  },

  onShow() {
    if (auth.current()) this.restore()
  },

  restore() {
    return this.perform('restore', async () => {
      this.setData({ screen: 'restore' })
      this.showCandidate(await auth.me())
    })
  },

  login() {
    return this.perform('login', async () => this.finishSignIn(await auth.login()))
  },

  submit() {
    const { screen, displayName, identifier, password } = this.data
    return this.perform('submit', async () => {
      switch (screen) {
        case 'create':
          this.finishSignIn(await auth.register(displayName))
          break
        case 'link':
          this.finishSignIn(await auth.link(identifier, password))
          break
        case 'credential':
          this.showCandidate(await auth.addWebCredential(identifier, password))
          break
      }
    })
  },

  logout() {
    return this.perform('logout', async () => {
      await auth.logout()
      this.setData({ screen: 'login', candidate: null })
    })
  },

  open(event) {
    if (this.data.pending) return
    this.setData({
      screen: event.currentTarget.dataset.screen,
      error: '',
      requestId: '',
      displayName: '',
      identifier: '',
      password: ''
    })
  },

  changeDisplayName(event) {
    this.setData({ displayName: event.detail.value })
  },

  changeIdentifier(event) {
    this.setData({ identifier: event.detail.value })
  },

  changePassword(event) {
    this.setData({ password: event.detail.value })
  },

  showCandidate(candidate) {
    this.setData({ screen: 'account', candidate, displayName: '', identifier: '', password: '' })
  },

  finishSignIn(candidate) {
    this.showCandidate(candidate)
    if (this.returnAfterSignIn && getCurrentPages().length > 1) wx.navigateBack()
  },

  /** 串行提交，失败时只在当前页面展示服务端错误和关联编号。 */
  async perform(action, work) {
    if (this.data.pending) return
    this.setData({ pending: action, error: '', requestId: '' })
    try {
      await work()
    } catch (error) {
      if (error.code === 'MINI_ACCOUNT_REQUIRED') {
        this.setData({ screen: 'choose' })
        return
      }
      if (error.code === 'SESSION_REQUIRED') {
        this.setData({ screen: 'login', candidate: null })
      }
      if (error.code === 'WEB_CREDENTIAL_EXISTS') {
        this.showCandidate({ ...this.data.candidate, hasWebCredential: true })
      }
      this.setData({
        error: error.code ? error.message : '操作暂未完成，请重试',
        requestId: error.requestId || ''
      })
    } finally {
      this.setData({ pending: '', password: '' })
    }
  }
})
