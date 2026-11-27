const service = require('../../utils/exam-service')
const { dateTime } = require('../../utils/format')
const { initial, load } = require('../../utils/page-state')

Page({
  data: { ...initial, ticket: null, rows: [] },
  onLoad({ id }) { this.id = id },
  onShow() { this.refresh() },
  refresh() {
    return load(this, async () => {
      const ticket = await service.getTicket(this.id)
      return {
        ticket,
        rows: [
          { label: '考生姓名', value: ticket.candidateName },
          { label: '考试开始', value: dateTime(ticket.examStartsAt) },
          { label: '考试结束', value: dateTime(ticket.examEndsAt) },
          { label: '考点', value: ticket.siteName },
          { label: '地址', value: ticket.siteAddress }
        ].filter((item) => item.value)
      }
    })
  }
})
