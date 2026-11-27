const { UI_PREVIEW } = require('./preview-config')

function failure(error) {
  const states = { SESSION_REQUIRED: 'login', SERVICE_PENDING: 'pending', TICKET_NOT_OPEN: 'unavailable' }
  return {
    state: states[error.code] || 'error',
    message: error.code ? error.message : '暂时无法加载，请稍后重试',
    requestId: error.requestId || ''
  }
}

const initial = { preview: UI_PREVIEW, state: 'loading', message: '', requestId: '' }
const listInitial = { ...initial, items: [], page: 0, hasMore: false, loadingMore: false, moreError: '' }

/** 搜索或筛选快速切换时，只采用最后一次请求的结果。 */
async function load(page, fetchData) {
  const revision = (page.revision || 0) + 1
  page.revision = revision
  page.setData({ state: 'loading', message: '', requestId: '' })
  try {
    const data = await fetchData()
    if (page.revision === revision) page.setData({ state: 'ready', ...data })
  } catch (error) {
    if (page.revision === revision) page.setData(failure(error))
  }
}

async function loadList(page, fetchData, { append = false } = {}) {
  if (append && (!page.data.hasMore || page.data.loadingMore || page.data.state !== 'ready')) return
  const nextPage = append ? page.data.page + 1 : 1
  const fetchPage = async () => {
    const result = await fetchData(nextPage)
    const items = append ? [...page.data.items, ...result.items] : result.items
    return { items, page: nextPage, hasMore: items.length < result.total, loadingMore: false, moreError: '' }
  }
  if (!append) {
    page.setData({ ...listInitial })
    return load(page, fetchPage)
  }
  const revision = page.revision
  page.setData({ loadingMore: true, moreError: '' })
  try {
    const data = await fetchPage()
    if (page.revision === revision) page.setData(data)
  } catch (error) {
    if (page.revision !== revision) return
    const info = failure(error)
    page.setData(info.state === 'login'
      ? { ...listInitial, ...info }
      : { loadingMore: false, moreError: info.message })
  }
}

module.exports = { initial, listInitial, load, loadList, failure }
