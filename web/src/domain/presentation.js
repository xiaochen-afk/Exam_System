export const noticeCategories = {
  POLICY: '政策法规',
  NOTICE: '考务通知',
  GUIDE: '报考须知',
  FAQ: '常见答疑',
}

export const states = {
  UPCOMING: '即将开始',
  OPEN: '正在报名',
  FULL: '名额已满',
  CLOSED: '报名已截止',
  UNAVAILABLE: '暂不可报',
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  WITHDRAWN: '已下架',
  COMPLETED: '已结束',
  ACTIVE: '启用',
  INACTIVE: '停用',
  QUALIFIED: '已具备资格',
  UNQUALIFIED: '尚未取得资格',
  REGISTERED: '报名成功',
  STUDENT: '在校考生',
  SOCIAL: '社会考生',
  ASSIGNED: '已分配',
  UNASSIGNED: '待分配',
  UNSCORED: '未录入',
  SUCCESS: '成功',
  FAILED: '失败',
}

export function stateTone(value) {
  if (['OPEN', 'ACTIVE', 'REGISTERED', 'PUBLISHED', 'ASSIGNED', 'SUCCESS'].includes(value))
    return 'green'
  if (['UPCOMING', 'DRAFT', 'UNASSIGNED', 'UNSCORED'].includes(value)) return 'amber'
  if (['FULL', 'FAILED'].includes(value)) return 'red'
  return 'neutral'
}

const dateFormatter = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' })
const timedFormatter = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short',
  timeStyle: 'short',
  hourCycle: 'h23',
})

export function formatDate(input, timed = false) {
  if (!input) return '—'
  const date = new Date(input)
  return Number.isNaN(date.getTime()) ? '—' : (timed ? timedFormatter : dateFormatter).format(date)
}

export function toLocalInput(input) {
  if (!input) return ''
  const date = new Date(input)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
