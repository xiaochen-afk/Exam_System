const service = require('../../utils/exam-service')
const format = require('../../utils/format')
const { initial, load } = require('../../utils/page-state')

Page({
  data: { ...initial, item: null, rows: [] },
  onLoad({ id }) { this.id = id },
  onShow() { this.refresh() },
  refresh() {
    return load(this, async () => {
      const item = format.registration(await service.getRegistration(this.id))
      return {
        item,
        rows: [
          { label: '报考科目', value: item.subjectName },
          { label: '报考考点', value: item.siteName },
          { label: '考试时间', value: item.examTime },
          { label: '提交时间', value: item.submittedTime }
        ]
      }
    })
  }
})
