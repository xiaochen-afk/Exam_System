const service = require('../../utils/exam-service')
const format = require('../../utils/format')
const { initial, load, failure } = require('../../utils/page-state')

Page({
  data: {
    ...initial, profile: null, typeLabel: '', options: [], selected: null, existing: null,
    submitting: false, formError: '', actionRequestId: ''
  },
  onLoad({ id }) { this.id = id },
  onShow() { if (!this.data.submitting) this.refresh() },
  refresh() {
    const previous = this.data.selected?.id
    return load(this, async () => {
      const profile = await service.getProfile()
      const base = { profile, typeLabel: format.candidateTypes[profile.candidateType] || '', formError: '', actionRequestId: '' }
      if (!profile.candidateType || (profile.candidateType === 'STUDENT' && !profile.schoolId)) {
        return { ...base, options: [], selected: null, existing: null }
      }
      const result = await service.registrationOptions(this.id)
      const options = result.items.map(format.exam)
      const existing = options.find((item) => item.registration)?.registration || null
      const selected = options.find((item) => item.selectable && item.id === previous)
        || options.find((item) => item.selectable && item.recommended)
        || null
      return { ...base, options, selected, existing }
    })
  },
  pickSite(event) {
    if (this.data.submitting) return
    const selected = this.data.options.find((item) => item.id === event.currentTarget.dataset.id && item.selectable)
    if (selected) this.setData({ selected, formError: '', actionRequestId: '' })
  },
  async submit() {
    const { selected, submitting } = this.data
    if (!selected || submitting) return
    this.setData({ submitting: true, formError: '', actionRequestId: '' })
    try {
      const confirmation = await wx.showModal({
        title: this.data.preview ? '确认演示报名' : '确认报名',
        content: selected.name + '\n' + selected.siteName + '\n' + selected.examTime,
        confirmText: '确认提交',
        confirmColor: '#0b63d8'
      })
      if (!confirmation.confirm) return
      const receipt = await service.submitRegistration(selected.id)
      wx.redirectTo({ url: '/pages/record-detail/record-detail?id=' + encodeURIComponent(receipt.id) })
    } catch (error) {
      const info = failure(error)
      if (info.state === 'login') this.setData(info)
      else this.setData({ formError: info.message, actionRequestId: info.requestId })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
