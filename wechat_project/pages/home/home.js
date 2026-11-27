const service = require('../../utils/exam-service')
const format = require('../../utils/format')
const { initial, load } = require('../../utils/page-state')
const { selectTab } = require('../../utils/tab-bar')

Page({
  data: {
    ...initial, exams: [], notices: [],
    shortcuts: [
      { label: '我的报名', icon: 'list', url: '/pages/records/records' },
      { label: '准考证', icon: 'ticket', url: '/pages/records/records?mode=tickets' },
      { label: '成绩查询', icon: 'chart', url: '/pages/records/records?mode=scores' },
      { label: '报考资料', icon: 'user', url: '/pages/profile/profile' }
    ]
  },
  onLoad() { this.refresh() },
  onShow() { selectTab(this) },
  refresh() {
    return load(this, async () => {
      const [exams, notices] = await Promise.all([
        service.listExams({ size: 2 }), service.listNotices({ size: 2 })
      ])
      return { exams: exams.items.map(format.exam), notices: notices.items.map(format.notice) }
    })
  }
})
