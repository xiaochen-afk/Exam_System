const { listInitial, loadList } = require('./page-state')
const { selectTab } = require('./tab-bar')

function catalogPage(filters, filterKey, fetchList, format) {
  return {
    data: {
      ...listInitial, draft: '', keyword: '', selected: '',
      filters: filters.map(([value, label]) => ({ value, label }))
    },
    onLoad() { this.refresh() },
    onShow() { selectTab(this) },
    onReachBottom() { this.more() },
    changeKeyword(event) { this.setData({ draft: event.detail.value }) },
    search() {
      this.setData({ keyword: this.data.draft.trim() })
      this.refresh()
    },
    pickFilter(event) {
      this.setData({ selected: event.currentTarget.dataset.value })
      this.refresh()
    },
    query(append) {
      const { keyword, selected } = this.data
      return loadList(this, async (page) => {
        const data = { page, size: 12 }
        if (keyword) data.keyword = keyword
        if (selected) data[filterKey] = selected
        const result = await fetchList(data)
        return { ...result, items: result.items.map(format) }
      }, { append })
    },
    refresh() { return this.query(false) },
    more() { return this.query(true) }
  }
}

module.exports = { catalogPage }
