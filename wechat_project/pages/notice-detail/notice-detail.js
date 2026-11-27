const service = require('../../utils/exam-service')
const format = require('../../utils/format')
const { initial, load, failure } = require('../../utils/page-state')

Page({
  data: { ...initial, item: null, formError: '', actionRequestId: '' },
  onLoad({ id }) {
    this.id = id
    this.refresh()
  },
  refresh() {
    return load(this, async () => ({
      item: format.notice(await service.getNotice(this.id)), formError: '', actionRequestId: ''
    }))
  },
  async openAttachment(event) {
    try {
      await service.openNoticeAttachment(this.id, event.currentTarget.dataset.id)
    } catch (error) {
      const info = failure(error)
      this.setData({ formError: info.message, actionRequestId: info.requestId })
    }
  }
})
