const service = require('../../utils/exam-service')
const format = require('../../utils/format')
const { initial, load } = require('../../utils/page-state')

Page({
  data: { ...initial, item: null },
  onLoad({ id }) {
    this.id = id
    this.refresh()
  },
  refresh() {
    return load(this, async () => {
      const item = format.exam(await service.getExam(this.id))
      return { item }
    })
  }
})
