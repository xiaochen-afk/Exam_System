const service = require('../../utils/exam-service')
const format = require('../../utils/format')
const { listInitial, loadList } = require('../../utils/page-state')

const modes = {
  registrations: { title: '我的报名', empty: '你还没有报名记录' },
  tickets: { title: '准考证', empty: '有报名记录后，可在这里查看对应准考证' },
  scores: { title: '成绩查询', empty: '暂无已发布的个人成绩' },
  messages: { title: '消息中心', empty: '目前没有考试进度消息' }
}

Page({
  data: { ...listInitial, mode: 'registrations', ...modes.registrations },
  onLoad({ mode }) {
    if (!Object.prototype.hasOwnProperty.call(modes, mode)) mode = 'registrations'
    this.setData({ mode, ...modes[mode] })
    wx.setNavigationBarTitle({ title: modes[mode].title })
  },
  onShow() { this.refresh() },
  onReachBottom() { this.more() },
  query(append) {
    const mode = this.data.mode
    return loadList(this, async (page) => {
      const query = { page, size: 12 }
      if (mode === 'messages') return service.listMessages(query)
      if (mode === 'scores') {
        const result = await service.listScores(query)
        return { ...result, items: result.items.map((item) => ({ ...item, publishedTime: format.dateTime(item.publishedAt) })) }
      }
      const result = await service.listRegistrations(query)
      return { ...result, items: result.items.map(format.registration) }
    }, { append })
  },
  refresh() { return this.query(false) },
  more() { return this.query(true) }
})
