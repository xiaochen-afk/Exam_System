const service = require('../../utils/exam-service')
const { initial, load, failure } = require('../../utils/page-state')

Page({
  data: {
    ...initial, candidateType: '', schoolId: null, schoolName: '', editingSchool: false,
    draft: '', keyword: '', schools: [], schoolPage: 0, hasMoreSchools: false, loadingSchools: false,
    saving: false, saved: false, formError: '', actionRequestId: '', schoolError: ''
  },
  onShow() {
    if (this.data.state === 'loading' || this.data.state === 'login') this.refresh()
  },
  refresh() {
    return load(this, async () => {
      const profile = await service.getProfile()
      return { ...profile, formError: '', actionRequestId: '' }
    })
  },
  pickType(event) {
    if (this.data.saving) return
    const candidateType = event.currentTarget.dataset.type
    this.setData({
      candidateType, saved: false, editingSchool: false, formError: '',
      ...(candidateType === 'SOCIAL' ? { schoolId: null, schoolName: '' } : {})
    })
  },
  toggleSchools() {
    if (this.data.saving) return
    const editingSchool = !this.data.editingSchool
    this.setData({ editingSchool })
    if (editingSchool) this.searchSchools()
  },
  changeKeyword(event) { this.setData({ draft: event.detail.value }) },
  searchSchools() {
    this.setData({ keyword: this.data.draft.trim(), schools: [], schoolPage: 0, hasMoreSchools: false })
    return this.fetchSchools(false)
  },
  moreSchools() {
    if (!this.data.loadingSchools) return this.fetchSchools(true)
  },
  async fetchSchools(append) {
    const revision = (this.schoolRevision || 0) + 1
    this.schoolRevision = revision
    const page = append ? this.data.schoolPage + 1 : 1
    this.setData({ loadingSchools: true, schoolError: '' })
    try {
      const result = await service.listSchools({ keyword: this.data.keyword, page, size: 10 })
      if (revision !== this.schoolRevision) return
      const schools = append ? [...this.data.schools, ...result.items] : result.items
      this.setData({ schools, schoolPage: page, hasMoreSchools: schools.length < result.total })
    } catch (error) {
      if (revision !== this.schoolRevision) return
      if (error.code === 'SESSION_REQUIRED') this.setData(failure(error))
      else this.setData({ schoolError: failure(error).message })
    } finally {
      if (revision === this.schoolRevision) this.setData({ loadingSchools: false })
    }
  },
  pickSchool(event) {
    const school = this.data.schools.find((item) => item.id === event.currentTarget.dataset.id)
    if (!school) return
    this.setData({ schoolId: school.id, schoolName: school.name, saved: false, editingSchool: false, formError: '' })
  },
  async save() {
    const { candidateType, schoolId, saving } = this.data
    if (saving || !candidateType || (candidateType === 'STUDENT' && !schoolId)) return
    this.setData({ saving: true, formError: '', actionRequestId: '' })
    try {
      const profile = await service.updateProfile({ candidateType, schoolId: candidateType === 'STUDENT' ? schoolId : null })
      this.setData({ ...profile, saved: true, editingSchool: false })
      wx.showToast({ title: '资料已保存', icon: 'success' })
    } catch (error) {
      const info = failure(error)
      if (info.state === 'login') this.setData(info)
      else this.setData({ formError: info.message, actionRequestId: info.requestId })
    } finally {
      this.setData({ saving: false })
    }
  }
})
