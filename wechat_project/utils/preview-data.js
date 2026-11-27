const { ApiError } = require('./api')

function sampleExam(id, name, subjectName, siteName, date, registrationStatus, siteAddress) {
  return {
    id, name, subjectName, siteName, siteAddress, registrationStatus,
    registrationStartsAt: '2026-11-20T09:00:00+08:00',
    registrationEndsAt: '2026-12-03T18:00:00+08:00',
    examStartsAt: `${date}T09:00:00+08:00`,
    examEndsAt: `${date}T11:00:00+08:00`,
    description: '请在报名截止前确认考点。考试安排和入场要求以准考证及考务通知为准。'
  }
}

const north = '示例大学·北校区'
const east = '示例大学·东校区'
const exams = [
  sampleExam('digital-north', '计算机应用基础考试', '计算机应用基础', north, '2026-12-12', 'OPEN', '北校区教学楼'),
  sampleExam('english', '英语综合能力考试', '英语综合能力', east, '2026-12-19', 'UPCOMING', '东校区外语楼'),
  sampleExam('digital-east', '计算机应用基础考试', '计算机应用基础', east, '2026-12-12', 'OPEN', '东校区综合楼'),
  sampleExam('professional', '职业能力测评', '职业能力', north, '2026-12-06', 'CLOSED', '北校区教学楼'),
  sampleExam('language', '语言应用能力测评', '语言应用能力', east, '2026-11-25', 'CLOSED', '东校区外语楼')
]
exams[1].registrationStartsAt = '2026-12-01T09:00:00+08:00'
exams[1].registrationEndsAt = '2026-12-09T18:00:00+08:00'
exams[3].registrationEndsAt = '2026-11-25T18:00:00+08:00'
exams[4].registrationEndsAt = '2026-11-23T18:00:00+08:00'

const schools = [{ id: 'demo-university', name: '示例大学' }, { id: 'demo-college', name: '示例职业学院' }]
const notices = [
  {
    id: 'winter', category: 'NOTICE', pinned: true, title: '冬季考试报名安排',
    summary: '查看冬季考试的报名时间、考点选择与报考流程。',
    publishedAt: '2026-11-26T09:00:00+08:00',
    body: '请在考试详情中查看各场次的报名起止时间。\n\n1. 报名前请确认报考资料。\n2. 选择开放考点并核对场次。\n3. 提交后在「我的报名」查看回执。',
    attachments: [{ id: 'sample-guide', filename: '报考流程（示例）.pdf' }]
  },
  {
    id: 'entry', category: 'GUIDE', pinned: false, title: '考生入场须知',
    summary: '请提前核对准考证上的考试时间、地点和入场要求。',
    publishedAt: '2026-11-24T10:00:00+08:00',
    body: '开放后可在「准考证」查看完整考场信息。\n\n请提前规划路线，携带规定的入场材料，并遵守考点工作人员的指引。'
  },
  {
    id: 'switch-site', category: 'FAQ', pinned: false, title: '在校考生可以选择其他考点吗？',
    summary: '本校考点可报时将被默认推荐，仍可以自行选择其他开放考点。',
    publishedAt: '2026-11-22T09:00:00+08:00',
    body: '在校考生可以选择其他开放考点。本校考点不可报时，请在同一项目的可报名场次中选择。'
  },
  {
    id: 'policy', category: 'POLICY', pinned: false, title: '报名信息填写说明',
    summary: '请选择对应考生类型，并在提交前核对考点和场次。',
    publishedAt: '2026-11-21T09:00:00+08:00',
    body: '考生应按本人实际情况填写资料。个人资料用于后续报名及考点推荐。'
  }
]
let candidate = null
let profile = { candidateType: 'STUDENT', schoolId: schools[0].id, schoolName: schools[0].name }

function receipt(item, id, number, submittedAt) {
  return {
    id, registrationNumber: number, sessionId: item.id, sessionName: item.name,
    subjectName: item.subjectName, siteName: item.siteName, examStartsAt: item.examStartsAt,
    submittedAt, status: 'REGISTERED'
  }
}

const initialRegistrations = [
  receipt(exams[3], 'ticket-demo', 'DEMO-001', '2026-11-24T12:38:00+08:00'),
  receipt(exams[4], 'score-demo', 'DEMO-002', '2026-11-22T10:22:00+08:00')
]
let registrations = initialRegistrations.slice()

function find(items, id) {
  const result = items.find((item) => item.id === id)
  if (!result) throw new ApiError('NOT_FOUND', '未找到该记录')
  return result
}

function matches(value, keyword) {
  return !keyword || value.toLowerCase().includes(keyword.trim().toLowerCase())
}

function paginate(items, { page = 1, size = 20 } = {}) {
  return { items: items.slice((page - 1) * size, page * size), page, size, total: items.length }
}

function listExams(query = {}) {
  return paginate(exams.filter((item) =>
    matches(item.name + item.subjectName, query.keyword) &&
    (!query.registrationStatus || item.registrationStatus === query.registrationStatus)), query)
}

function listNotices(query = {}) {
  return paginate(notices.filter((item) =>
    matches(item.title, query.keyword) && (!query.category || item.category === query.category)), query)
}

function updateProfile(input) {
  const school = input.candidateType === 'STUDENT' ? find(schools, input.schoolId) : null
  profile = { candidateType: input.candidateType, schoolId: school?.id || null, schoolName: school?.name || null }
  return profile
}

function registrationOptions(id) {
  const exam = find(exams, id)
  const related = exams.filter((item) => item.name === exam.name)
  const registration = registrations.find((item) => related.some((option) => option.id === item.sessionId))
  return {
    items: related.map((item) => {
      const selectable = item.registrationStatus === 'OPEN' && !registration
      return {
        ...item,
        selectable,
        recommended: selectable && profile.schoolId === schools[0].id && item.siteName === north,
        registration: registration?.sessionId === item.id ? registration : null,
        unavailableReason: registration ? '本项目已报名' : selectable ? null : '当前场次暂不可报名'
      }
    })
  }
}

function submitRegistration(id) {
  const item = find(registrationOptions(id).items, id)
  if (!item.selectable) throw new ApiError('REGISTRATION_UNAVAILABLE', item.unavailableReason)
  const number = String(registrations.length + 1).padStart(3, '0')
  const record = receipt(item, 'new-demo-' + id, 'DEMO-' + number, new Date().toISOString())
  registrations = [record, ...registrations]
  return record
}

function getTicket(id) {
  find(registrations, id)
  if (id !== 'ticket-demo') throw new ApiError('TICKET_NOT_OPEN', '该场次的准考证暂未开放')
  return {
    ticketNumber: 'TICKET-DEMO', candidateName: '示例考生', subjectName: exams[3].subjectName,
    examStartsAt: exams[3].examStartsAt, examEndsAt: exams[3].examEndsAt,
    siteName: north, siteAddress: exams[3].siteAddress, roomName: '第 02 考场', seatNumber: '16',
    instructions: '请在考试开始前到达考点，按现场指引入场。'
  }
}

function signIn(hasWebCredential = true) {
  candidate = { id: 'demo-candidate', displayName: '示例考生', hasWebCredential }
  return candidate
}

function signOut() {
  candidate = null
  profile = { candidateType: 'STUDENT', schoolId: schools[0].id, schoolName: schools[0].name }
  registrations = initialRegistrations.slice()
}

module.exports = {
  account: () => candidate,
  signIn,
  signOut,
  listExams,
  getExam: (id) => find(exams, id),
  listNotices,
  getNotice: (id) => ({ publisher: '示例考试服务中心', attachments: [], ...find(notices, id) }),
  openNoticeAttachment() { throw new ApiError('SERVICE_PENDING', '示例附件仅用于展示，下载待接入') },
  getProfile: () => profile,
  updateProfile,
  listSchools: (query) => paginate(schools.filter((item) => matches(item.name, query.keyword)), query),
  registrationOptions,
  submitRegistration,
  listRegistrations: (query) => paginate(registrations, query),
  getRegistration: (id) => find(registrations, id),
  getTicket,
  listScores: (query) => paginate([{
    id: 'example-score', registrationId: 'score-demo', sessionName: exams[4].name,
    subjectName: exams[4].subjectName, score: 86, publishedAt: '2026-11-27T09:00:00+08:00'
  }], query),
  listMessages: (query) => paginate([], query)
}
