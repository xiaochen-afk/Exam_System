const account = require('../../utils/account')
const { UI_PREVIEW } = require('../../utils/preview-config')
const { selectTab } = require('../../utils/tab-bar')

Page({
  data: {
    preview: UI_PREVIEW, signedIn: false, displayName: '',
    messageMenu: { label: '消息中心', icon: 'bell', url: '/pages/records/records?mode=messages' },
    tools: [
      { label: '报考资料', icon: 'user', url: '/pages/profile/profile' },
      { label: '我的报名', icon: 'list', url: '/pages/records/records' },
      { label: '准考证', icon: 'ticket', url: '/pages/records/records?mode=tickets' },
      { label: '成绩查询', icon: 'chart', url: '/pages/records/records?mode=scores' }
    ]
  },
  onShow() {
    selectTab(this)
    const current = account.current()
    this.setData({ signedIn: Boolean(current), displayName: current?.displayName || '' })
  }
})
