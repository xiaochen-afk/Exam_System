const examStatuses = {
  OPEN: ['报名中', 'green'],
  UPCOMING: ['即将报名', 'gold'],
  FULL: ['已满', 'muted'],
  CLOSED: ['已截止', 'muted'],
  UNAVAILABLE: ['暂不可报', 'muted']
}

const noticeCategories = { NOTICE: '考务通知', POLICY: '政策法规', GUIDE: '报考须知', FAQ: '常见答疑' }
const candidateTypes = { STUDENT: '在校考生', SOCIAL: '社会考生' }

/** 接口时间为 ISO 时间，所有页面按北京时间显示。 */
function dateTime(value) {
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return '待公布'
  return new Date(timestamp + 8 * 3600000).toISOString().slice(0, 16).replace('T', ' ')
}

function exam(item) {
  const [statusLabel, statusTone] = examStatuses[item.registrationStatus] || examStatuses.UNAVAILABLE
  const examTime = dateTime(item.examStartsAt)
  return {
    ...item,
    statusLabel,
    statusTone,
    examTime,
    monthDay: examTime === '待公布' ? '—' : examTime.slice(5, 10).replace('-', '.'),
    examEnd: dateTime(item.examEndsAt),
    registrationStart: dateTime(item.registrationStartsAt),
    registrationEnd: dateTime(item.registrationEndsAt)
  }
}

function notice(item) {
  return { ...item, categoryLabel: noticeCategories[item.category] || '资讯', date: dateTime(item.publishedAt).slice(0, 10) }
}

function registration(item) {
  return { ...item, examTime: dateTime(item.examStartsAt), submittedTime: dateTime(item.submittedAt) }
}

module.exports = { exam, notice, registration, dateTime, candidateTypes, examStatuses, noticeCategories }
