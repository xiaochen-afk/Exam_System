// This in-memory transport is imported only by the Vite development build.
import { ApiError } from './client.js'
import {
  notices,
  subjects,
  sites,
  schools,
  rooms,
  sessions,
  registrations,
  roleCatalog,
  users,
} from './demo-fixtures.js'
import { validateAccount } from '@/domain/account.js'
import { validateSession } from '@/domain/admin.js'

const candidateAccounts = [
  { id: 'candidate-1', identifier: 'candidate.demo', displayName: '演示考生' },
]
const passwords = new Map([...users, ...candidateAccounts].map((user) => [user.id, 'Demo2026']))
const candidateProfiles = new Map()
const seating = new Map()
const scores = new Map()
const ticketPublication = new Set()
const scorePublication = new Set()
const auditLogs = []
let currentId

const fail = (status, code, message) => {
  throw new ApiError(status, code, message, 'demo-request')
}
const bad = (message) => fail(400, 'VALIDATION_FAILED', message)
const forbidden = () => fail(403, 'FORBIDDEN', '当前账号无权执行此操作')
const missing = () => fail(404, 'NOT_FOUND', '该记录不存在或不在当前授权范围内')
const conflict = (code, message) => fail(409, code, message)
const find = (rows, id) => rows.find((row) => row.id === id)
const byId = (rows, id) => find(rows, id) ?? missing()
const id = () => crypto.randomUUID()
const selection = (value, expected) => !expected || expected === value
const matches = (value, keyword) =>
  !keyword ||
  String(value ?? '')
    .toLowerCase()
    .includes(keyword.toLowerCase())
const publicSession = (row) => ['PUBLISHED', 'COMPLETED'].includes(row.status)

function pageOf(rows, query, extra = {}) {
  const page = Number(query.page ?? 1)
  const size = Number(query.size ?? 20)
  if (!Number.isSafeInteger(page) || page < 1 || !Number.isInteger(size) || size < 1 || size > 100)
    bad('分页参数无效')
  return {
    items: rows.slice((page - 1) * size, page * size),
    page,
    size,
    total: rows.length,
    ...extra,
  }
}

function identity() {
  const candidate = find(candidateAccounts, currentId)
  if (candidate)
    return {
      id: candidate.id,
      displayName: candidate.displayName,
      roles: ['CANDIDATE'],
      permissions: [],
    }
  const user = find(users, currentId)
  if (!user || user.status !== 'ACTIVE') fail(401, 'SESSION_REQUIRED', '请重新登录')
  const roles = []
  if (user.roleCodes.some((code) => code !== 'SYSTEM_ADMIN')) roles.push('EXAM_STAFF')
  if (user.roleCodes.includes('SYSTEM_ADMIN')) roles.push('ADMIN')
  const permissions = [
    ...new Set(user.roleCodes.flatMap((code) => byId(roleCatalog, code).permissions)),
  ]
  return { id: user.id, displayName: user.displayName, roles, permissions }
}

function grants(permission) {
  return identity().permissions.includes(permission)
}

function requirePermission(permission) {
  if (!grants(permission)) forbidden()
}

function inScope(siteId, sessionId) {
  const user = find(users, currentId)
  if (user.roleCodes.includes('ORGANIZER')) {
    return (
      (!user.siteIds.length || user.siteIds.includes(siteId)) &&
      (!sessionId || !user.sessionIds.length || user.sessionIds.includes(sessionId))
    )
  }
  return (
    user.roleCodes.includes('SITE_MANAGER') &&
    user.siteIds.includes(siteId) &&
    (sessionId
      ? user.sessionIds.includes(sessionId)
      : sessions.some((row) => row.siteId === siteId && user.sessionIds.includes(row.id)))
  )
}

function writableSession(session) {
  return inScope(session.siteId, session.id) ? session : missing()
}

function registrationRows(query = {}) {
  return registrations.filter(
    (row) =>
      inScope(row.siteId, row.sessionId) &&
      selection(row.sessionId, query.sessionId) &&
      selection(row.siteId, query.siteId) &&
      selection(row.status, query.status) &&
      selection(row.candidateType, query.candidateType) &&
      matches(row.registrationNumber, query.registrationNumber),
  )
}

function sessionSummary(row) {
  const subject = byId(subjects, row.subjectId)
  const site = byId(sites, row.siteId)
  const count = registrations.filter(
    (reg) => reg.sessionId === row.id && reg.status === 'REGISTERED',
  ).length
  const remainingCapacity = Math.max(0, row.capacity - count)
  const now = Date.now()
  let registrationStatus = 'CLOSED'
  if (row.status === 'PUBLISHED' && (site.status !== 'ACTIVE' || subject.status !== 'ACTIVE')) {
    registrationStatus = 'UNAVAILABLE'
  } else if (row.status === 'PUBLISHED' && now < Date.parse(row.registrationStartsAt))
    registrationStatus = 'UPCOMING'
  else if (
    row.status === 'PUBLISHED' &&
    now >= Date.parse(row.registrationStartsAt) &&
    now < Date.parse(row.registrationEndsAt)
  ) {
    registrationStatus = remainingCapacity > 0 ? 'OPEN' : 'FULL'
  }
  return {
    subjectName: subject.name,
    siteName: site.name,
    siteAddress: site.address,
    count,
    registrationStatus,
  }
}

function publicSummary(row) {
  const { subjectName, siteName, siteAddress, registrationStatus } = sessionSummary(row)
  const {
    id,
    name,
    registrationStartsAt,
    registrationEndsAt,
    examStartsAt,
    examEndsAt,
    description,
  } = row
  return {
    id,
    name,
    subjectName,
    siteName,
    siteAddress,
    registrationStatus,
    registrationStartsAt,
    registrationEndsAt,
    examStartsAt,
    examEndsAt,
    description,
  }
}

function registrationDto(row) {
  const session = byId(sessions, row.sessionId)
  const { subjectName, siteName } = sessionSummary(session)
  const {
    id,
    registrationNumber,
    candidateName,
    candidateType,
    schoolName,
    sessionId,
    siteId,
    submittedAt,
    status,
  } = row
  return {
    id,
    registrationNumber,
    candidateName,
    candidateType,
    schoolName,
    sessionId,
    siteId,
    submittedAt,
    status,
    subjectName,
    siteName,
    sessionName: session.name,
  }
}

const unavailableReasons = {
  UPCOMING: '该场次报名尚未开始',
  CLOSED: '该场次报名已截止',
  FULL: '该场次已报满，请选择其他场次',
  UNAVAILABLE: '该考点或科目暂不可报',
}

function sameApplication(a, b) {
  return (
    a.id === b.id ||
    Boolean(
      a.applicationCode && a.applicationCode === b.applicationCode && a.subjectId === b.subjectId,
    )
  )
}

function ownRegistration(row) {
  const session = byId(sessions, row.sessionId)
  const { id, registrationNumber, status, submittedAt, sessionId } = row
  const { subjectName, siteName } = sessionSummary(session)
  return {
    id,
    registrationNumber,
    status,
    submittedAt,
    sessionId,
    sessionName: session.name,
    subjectName,
    siteName,
    examStartsAt: session.examStartsAt,
  }
}

function candidateProfile(candidateId) {
  const profile = candidateProfiles.get(candidateId)
  const school = find(schools, profile?.schoolId)
  return {
    candidateType: profile?.candidateType ?? null,
    schoolId: school?.id ?? null,
    schoolName: school?.name ?? null,
  }
}

function writeCandidateProfile(candidateId, body) {
  const { candidateType, schoolId } = body
  if (Object.keys(body).some((key) => !['candidateType', 'schoolId'].includes(key)))
    bad('提交了不允许修改的字段')
  if (candidateType === 'STUDENT') {
    if (!find(schools, schoolId)) bad('请从学校目录选择在读学校')
  } else if (candidateType !== 'SOCIAL' || schoolId !== null) {
    bad('请正确填写考生类型与学校')
  }
  candidateProfiles.set(candidateId, { candidateType, schoolId })
  return candidateProfile(candidateId)
}

function candidateContent(path, { method, query, body, sessionId, action }) {
  const candidate = identity()
  if (!candidate.roles.includes('CANDIDATE')) forbidden()
  if (path === '/schools' && method === 'GET') {
    return pageOf(
      schools.filter((school) => matches(school.name, query.keyword)),
      query,
    )
  }
  if (path === '/me/candidate-profile') {
    if (method === 'GET') return candidateProfile(candidate.id)
    if (method === 'PUT') return writeCandidateProfile(candidate.id, body)
    missing()
  }
  const own = registrations.filter((row) => row.candidateId === candidate.id)
  if (path === '/me/registrations' && method === 'GET') {
    return pageOf(
      own
        .map(ownRegistration)
        .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt) || a.id.localeCompare(b.id)),
      query,
    )
  }
  const session = byId(sessions.filter(publicSession), sessionId)
  const profile = candidateProfile(candidate.id)
  const registered = own.find(
    (row) => row.status === 'REGISTERED' && sameApplication(session, byId(sessions, row.sessionId)),
  )
  if (action === 'registration-options' && method === 'GET') {
    const items = sessions
      .filter((row) => publicSession(row) && sameApplication(row, session))
      .map((row) => {
        const summary = publicSummary(row)
        const selectable =
          summary.registrationStatus === 'OPEN' && Boolean(profile.candidateType) && !registered
        const recommended =
          selectable &&
          profile.candidateType === 'STUDENT' &&
          byId(sites, row.siteId).schoolId === profile.schoolId
        const registration = registered?.sessionId === row.id ? ownRegistration(registered) : null
        const unavailableReason = registered
          ? '此报考项目已有报名记录'
          : !profile.candidateType
            ? '请先完善报考资料'
            : (unavailableReasons[summary.registrationStatus] ?? null)
        return { ...summary, selectable, recommended, registration, unavailableReason }
      })
    items.sort(
      (a, b) =>
        Number(b.recommended) - Number(a.recommended) ||
        Number(b.selectable) - Number(a.selectable) ||
        Date.parse(a.examStartsAt) - Date.parse(b.examStartsAt) ||
        a.id.localeCompare(b.id),
    )
    return { items }
  }
  if (action !== 'registrations' || method !== 'POST') missing()
  if (Object.keys(body ?? {}).length) bad('报名请求不需要身份、考点或时间字段')
  if (registered) conflict('ALREADY_REGISTERED', '您已有该报考项目的报名记录')
  if (!profile.candidateType) conflict('PROFILE_REQUIRED', '请先完善报考资料')
  const { registrationStatus } = sessionSummary(session)
  if (registrationStatus === 'FULL') conflict('CAPACITY_FULL', unavailableReasons.FULL)
  if (registrationStatus !== 'OPEN')
    conflict('REGISTRATION_UNAVAILABLE', unavailableReasons[registrationStatus])
  // The demo commits synchronously. The backend must enforce capacity and uniqueness atomically.
  const recordId = id()
  const row = {
    id: recordId,
    registrationNumber: 'BM-DEMO-' + recordId.slice(0, 8).toUpperCase(),
    candidateId: candidate.id,
    candidateName: Array.from(candidate.displayName)[0] + '*',
    candidateType: profile.candidateType,
    schoolName: profile.schoolName,
    sessionId: session.id,
    siteId: session.siteId,
    submittedAt: new Date().toISOString(),
    status: 'REGISTERED',
  }
  registrations.push(row)
  return ownRegistration(row)
}

function record(action, targetLabel, scopeLabel = '系统') {
  auditLogs.unshift({
    id: id(),
    operatorName: identity().displayName,
    action,
    targetLabel,
    scopeLabel,
    occurredAt: new Date().toISOString(),
    result: 'SUCCESS',
  })
}

function accounts(path, method, body) {
  if (path === '/me' && method === 'GET') return identity()
  if (path === '/web-sessions/current' && method === 'DELETE') {
    currentId = undefined
    return
  }
  if (path === '/web-sessions' && method === 'POST') {
    if (!['STAFF', 'CANDIDATE'].includes(body.portal)) bad('登录入口无效')
    const collection = body.portal === 'STAFF' ? users : candidateAccounts
    const user = collection.find(
      ({ identifier }) => identifier.toLowerCase() === body.identifier.toLowerCase(),
    )
    if (!user || passwords.get(user.id) !== body.password || user.status === 'INACTIVE') {
      fail(401, 'INVALID_CREDENTIALS', '账号或密码有误，或账号已停用')
    }
    currentId = user.id
    return identity()
  }
  if (path === '/candidate-accounts' && method === 'POST') {
    const error = validateAccount({ ...body, confirmPassword: body.password }, true)
    if (error) bad(error)
    if (
      [...candidateAccounts, ...users].some(
        (account) => account.identifier.toLowerCase() === body.identifier.toLowerCase(),
      )
    ) {
      conflict('IDENTIFIER_TAKEN', '该登录账号已存在')
    }
    const user = { id: id(), identifier: body.identifier, displayName: body.displayName }
    candidateAccounts.push(user)
    passwords.set(user.id, body.password)
    return { id: user.id }
  }
  missing()
}

function publicContent(parts, query) {
  const [resource, itemId, subresource, fileId] = parts
  if (resource === 'notices') {
    if (itemId) {
      const item = byId(notices, itemId)
      if (subresource === 'attachments') {
        byId(item.attachments, fileId)
        return new Blob(
          [
            '考试服务平台 · 演示附件\n\n1. 查看考务通知\n2. 提前核对考场和入场时间\n3. 携带准考证及所需证件',
          ],
          { type: 'text/plain;charset=utf-8' },
        )
      }
      return item
    }
    const rows = notices.filter(
      (notice) =>
        selection(notice.category, query.category) &&
        matches(notice.title, query.keyword) &&
        (!query.featured || String(notice.featured) === query.featured) &&
        (!query.hasAttachments ||
          String(Boolean(notice.attachments.length)) === query.hasAttachments),
    )
    rows.sort(
      (a, b) => Number(b.pinned) - Number(a.pinned) || b.publishedAt.localeCompare(a.publishedAt),
    )
    return pageOf(
      rows.map(({ id, title, category, summary, pinned, publishedAt }) => ({
        id,
        title,
        category,
        summary,
        pinned,
        publishedAt,
      })),
      query,
    )
  }
  if (resource === 'exam-sessions') {
    const available = sessions.filter(publicSession)
    if (itemId) return publicSummary(byId(available, itemId))
    const rows = available
      .map(publicSummary)
      .filter(
        (row) =>
          matches(row.name + ' ' + row.subjectName, query.keyword) &&
          selection(row.registrationStatus, query.registrationStatus),
      )
    const priority = { OPEN: 0, UPCOMING: 1, FULL: 2, CLOSED: 3, UNAVAILABLE: 4 }
    rows.sort(
      (a, b) =>
        priority[a.registrationStatus] - priority[b.registrationStatus] ||
        Date.parse(a.examStartsAt) - Date.parse(b.examStartsAt) ||
        a.id.localeCompare(b.id),
    )
    return pageOf(rows, query)
  }
  if (resource === 'sites' && !itemId) {
    return pageOf(
      sites
        .filter(
          (site) =>
            site.status === 'ACTIVE' &&
            sessions.some((row) => row.siteId === site.id && row.status === 'PUBLISHED'),
        )
        .map(({ id, name, address }) => ({ id, name, address })),
      query,
    )
  }
  missing()
}

const optionsByContext = {
  registrations: { sessions: 'registrations.read', sites: 'registrations.read' },
  arrangements: { sessions: 'arrangements.read', rooms: 'arrangements.write' },
  scores: { sessions: 'scores.read' },
  sessions: { subjects: 'sessions.write', sites: 'sessions.write' },
  rooms: { sites: 'rooms.write' },
  users: { roles: 'users.write', sites: 'users.write', sessions: 'users.write' },
}

function lookup(query) {
  const { context, kind, sessionId } = query
  const permission = optionsByContext[context]?.[kind]
  if (!permission) bad('筛选项类型无效')
  requirePermission(permission)
  const catalog = { sessions, sites, rooms, subjects, roles: roleCatalog }[kind]
  let rows = catalog.filter((row) => row.status !== 'INACTIVE')
  if (kind === 'rooms') {
    const session = writableSession(byId(sessions, sessionId))
    rows = rows.filter((row) => row.siteId === session.siteId)
  } else if (context !== 'users') {
    if (kind === 'sessions')
      rows = rows.filter(
        (row) => inScope(row.siteId, row.id) && (context === 'registrations' || publicSession(row)),
      )
    if (kind === 'sites') rows = rows.filter((row) => inScope(row.id))
  }
  return rows.map(({ id, name, siteId, capacity }) => ({
    id,
    name: kind === 'sessions' ? name + ' · ' + byId(sites, siteId).name : name,
    ...(kind === 'rooms' ? { capacity } : {}),
  }))
}

function adminRegistrations(recordId, query) {
  requirePermission('registrations.read')
  const rows = registrationRows(query)
  if (recordId) return registrationDto(byId(rows, recordId))
  rows.sort(
    (a, b) => Date.parse(b.submittedAt) - Date.parse(a.submittedAt) || a.id.localeCompare(b.id),
  )
  return pageOf(rows.map(registrationDto), query, {
    summary: {
      total: rows.length,
      student: rows.filter((row) => row.candidateType === 'STUDENT').length,
      social: rows.filter((row) => row.candidateType === 'SOCIAL').length,
      assigned: rows.filter((row) => seating.has(row.id)).length,
    },
  })
}

const resources = {
  subjects: {
    rows: subjects,
    read: 'subjects.read',
    write: 'subjects.write',
    keys: ['code', 'name', 'description', 'status'],
  },
  sites: {
    rows: sites,
    read: 'sites.read',
    write: 'sites.write',
    create: 'sites.manage',
    keys: ['name', 'schoolName', 'address', 'status'],
  },
  rooms: {
    rows: rooms,
    read: 'rooms.read',
    write: 'rooms.write',
    keys: ['siteId', 'name', 'capacity', 'status'],
  },
  'exam-sessions': {
    rows: sessions,
    read: 'sessions.read',
    write: 'sessions.write',
    keys: [
      'siteId',
      'subjectId',
      'applicationCode',
      'name',
      'registrationStartsAt',
      'registrationEndsAt',
      'examStartsAt',
      'examEndsAt',
      'capacity',
      'description',
    ],
  },
  users: {
    rows: users,
    read: 'users.read',
    write: 'users.write',
    keys: [
      'identifier',
      'initialPassword',
      'displayName',
      'roleCodes',
      'siteIds',
      'sessionIds',
      'status',
    ],
  },
  'audit-logs': { rows: auditLogs, read: 'logs.read', keys: [] },
}

function adminResource(resource, recordId, method, query, body) {
  const config = resources[resource]
  if (!config) missing()
  const { rows, read, write, create, keys } = config
  const visible = rows.filter((row) => {
    if (resource === 'sites') return inScope(row.id)
    if (resource === 'rooms') return inScope(row.siteId)
    if (resource === 'exam-sessions') return inScope(row.siteId, row.id)
    return true
  })
  if (method === 'GET') {
    requirePermission(read)
    const selected = visible.filter(
      (row) =>
        selection(row.status, query.status) && matches(Object.values(row).join(' '), query.keyword),
    )
    return pageOf(
      selected.map((row) => {
        const capabilities = write && grants(write) ? ['EDIT'] : []
        if (resource === 'rooms')
          return { ...row, siteName: byId(sites, row.siteId).name, capabilities }
        if (resource === 'exam-sessions') {
          const summary = sessionSummary(row)
          const canPublish = row.status === 'DRAFT' && grants('sessions.publish')
          return {
            ...row,
            siteName: summary.siteName,
            subjectName: summary.subjectName,
            registrationCount: summary.count,
            capabilities: [...capabilities, ...(canPublish ? ['PUBLISH'] : [])],
          }
        }
        if (resource === 'users') {
          const { roleCodes, siteIds, sessionIds } = row
          return {
            ...row,
            roleLabel: roleCodes.map((code) => byId(roleCatalog, code).name).join('、'),
            scopeLabel: roleCodes.every((code) => code === 'SYSTEM_ADMIN')
              ? '系统管理'
              : siteIds.length || sessionIds.length
                ? `${siteIds.length} 个考点 / ${sessionIds.length} 个场次`
                : roleCodes.includes('ORGANIZER')
                  ? '主办方范围'
                  : '未授权考务范围',
            capabilities: row.id === currentId ? [] : capabilities,
          }
        }
        return { ...row, capabilities }
      }),
      query,
    )
  }
  if (!write || !['POST', 'PATCH'].includes(method)) missing()
  const previous = method === 'PATCH' ? byId(visible, recordId) : undefined
  const publishing = resource === 'exam-sessions' && body.status === 'PUBLISHED'
  requirePermission(publishing ? 'sessions.publish' : previous ? write : (create ?? write))
  if (publishing) {
    if (!previous || previous.status !== 'DRAFT') conflict('STATE_CONFLICT', '当前场次无法发布')
    if (
      validateSession(previous) ||
      byId(sites, previous.siteId).status !== 'ACTIVE' ||
      byId(subjects, previous.subjectId).status !== 'ACTIVE'
    )
      conflict('STATE_CONFLICT', '请确认场次时间、科目与考点状态')
    previous.status = 'PUBLISHED'
    record('发布考试场次', previous.name, byId(sites, previous.siteId).name)
    return { id: previous.id }
  }
  const allowedKeys = resource === 'sites' && !grants('sites.manage') ? ['address'] : keys
  if (Object.keys(body).some((key) => !allowedKeys.includes(key))) bad('提交了不允许修改的字段')
  const updated = { id: previous?.id ?? id(), ...previous, ...body }
  if (resource === 'exam-sessions')
    updated.applicationCode = (updated.applicationCode ?? '').trim().toUpperCase()
  validateResource(resource, updated, previous)
  if (resource === 'users') {
    if (updated.id === currentId) forbidden()
    if (previous && Object.hasOwn(body, 'identifier')) bad('不可修改登录标识')
    if (!previous) passwords.set(updated.id, updated.initialPassword)
    delete updated.initialPassword
  }
  if (resource === 'exam-sessions' && !previous) updated.status = 'DRAFT'
  if (previous) Object.assign(previous, updated)
  else rows.push(updated)
  record(
    (previous ? '更新' : '创建') +
      {
        'exam-sessions': '场次',
        users: '账号授权',
        sites: '考点',
        rooms: '考场',
        subjects: '科目',
      }[resource],
    updated.name ?? updated.displayName,
    updated.siteId ? byId(sites, updated.siteId).name : '系统',
  )
  return { id: updated.id }
}

function validateResource(resource, row, previous) {
  if (resource !== 'users' && !row.name?.trim()) bad('名称必填')
  if (resource === 'users') {
    if (!row.identifier?.trim() || !row.displayName?.trim() || !row.roleCodes?.length)
      bad('填写账号、姓名和角色')
    row.roleCodes.forEach((code) => byId(roleCatalog, code))
    row.siteIds.forEach((siteId) => byId(sites, siteId))
    row.sessionIds.forEach((sessionId) => byId(sessions, sessionId))
    if (!previous && (!row.initialPassword || row.initialPassword.length < 8))
      bad('初始密码至少 8 位')
    if (
      [...users, ...candidateAccounts].some(
        (user) =>
          user.id !== row.id && user.identifier.toLowerCase() === row.identifier.toLowerCase(),
      )
    ) {
      conflict('IDENTIFIER_TAKEN', '该登录账号已存在')
    }
  }
  if (
    resource === 'subjects' &&
    (!row.code || subjects.some((subject) => subject.id !== row.id && subject.code === row.code))
  ) {
    conflict('SUBJECT_CODE_CONFLICT', '科目代码为空或已被占用')
  }
  if (resource === 'sites' && (!row.address || !row.schoolName)) bad('填写考点地址和承办学校')
  if (resource === 'rooms' || resource === 'exam-sessions') {
    const site = byId(sites, row.siteId)
    if (!inScope(site.id)) forbidden()
    if (!Number.isInteger(row.capacity) || row.capacity <= 0) bad('容量须为正整数')
  }
  if (resource === 'rooms' && previous) {
    const assigned = [...seating.values()].filter((seat) => seat.roomId === row.id)
    if (
      assigned.length &&
      (row.siteId !== previous.siteId ||
        row.status !== 'ACTIVE' ||
        assigned.some((seat) => seat.seatNumber > row.capacity))
    ) {
      conflict('STATE_CONFLICT', '考场已有排考安排，无法保存此变更')
    }
  }
  if (resource === 'exam-sessions') {
    const subject = byId(subjects, row.subjectId)
    if (!/^[A-Z0-9_-]{0,32}$/.test(row.applicationCode))
      bad('报考项目代码可使用字母、数字、下划线与连字符')
    if (
      (!previous || row.subjectId !== previous.subjectId || row.siteId !== previous.siteId) &&
      (subject.status !== 'ACTIVE' || byId(sites, row.siteId).status !== 'ACTIVE')
    )
      bad('请选择启用的科目和考点')
    const count = previous ? sessionSummary(previous).count : 0
    const error = validateSession(row, { registrationCount: count })
    if (error) bad(error)
    if (
      count &&
      (row.siteId !== previous.siteId ||
        row.subjectId !== previous.subjectId ||
        row.applicationCode !== (previous.applicationCode ?? ''))
    )
      conflict('STATE_CONFLICT', '已有报名的场次不可更换科目、考点或报考项目代码')
  }
}

function dashboard() {
  const metrics = []
  if (grants('sessions.read'))
    metrics.push({
      key: 'sessions',
      value: sessions.filter((row) => inScope(row.siteId, row.id)).length,
    })
  if (grants('registrations.read'))
    metrics.push({ key: 'registrations', value: registrationRows().length })
  if (grants('arrangements.read'))
    metrics.push({
      key: 'assigned',
      value: registrationRows().filter((row) => seating.has(row.id)).length,
    })
  if (grants('users.read')) metrics.push({ key: 'users', value: users.length })
  return { metrics }
}

function roomRows(session) {
  return rooms.filter((room) => room.siteId === session.siteId && room.status === 'ACTIVE')
}

function workbench(type, query) {
  requirePermission(type + '.read')
  const session = writableSession(byId(sessions, query.sessionId))
  if (!publicSession(session)) missing()
  const rows = registrationRows({ sessionId: session.id, status: 'REGISTERED' })
  if (type === 'arrangements') {
    const items = rows.map(({ id, registrationNumber, candidateName }) => {
      const assignment = seating.get(id)
      return {
        id,
        registrationNumber,
        candidateName,
        ...assignment,
        roomName: assignment ? byId(rooms, assignment.roomId).name : null,
        arrangementStatus: assignment ? 'ASSIGNED' : 'UNASSIGNED',
      }
    })
    const ticketsPublished = ticketPublication.has(session.id)
    return pageOf(items, query, {
      summary: {
        total: rows.length,
        assigned: items.filter((item) => item.roomId).length,
        capacity: roomRows(session).reduce((total, room) => total + room.capacity, 0),
        ticketsPublished,
      },
      capabilities: ticketsPublished
        ? []
        : [
            ...(grants('arrangements.write') ? ['EDIT'] : []),
            ...(grants('tickets.publish') ? ['PUBLISH'] : []),
          ],
    })
  }
  const published = scorePublication.has(session.id)
  const items = rows.map(({ id, registrationNumber, candidateName }) => ({
    id,
    registrationNumber,
    candidateName,
    siteName: byId(sites, session.siteId).name,
    ...scores.get(id),
    scoreStatus: scores.has(id) ? (published ? 'PUBLISHED' : 'DRAFT') : 'UNSCORED',
  }))
  return pageOf(items, query, {
    summary: {
      total: rows.length,
      recorded: items.filter((item) => item.score != null).length,
      published,
    },
    capabilities: published
      ? []
      : [
          ['scores.write', 'EDIT'],
          ['scores.import', 'IMPORT'],
          ['scores.publish', 'PUBLISH'],
        ]
          .filter(([permission]) => grants(permission))
          .map(([, action]) => action),
  })
}

function assign(row, roomId, seatNumber) {
  if (ticketPublication.has(row.sessionId)) conflict('STATE_CONFLICT', '准考证已开放，排考已锁定')
  const room = byId(roomRows(byId(sessions, row.sessionId)), roomId)
  if (!Number.isInteger(seatNumber) || seatNumber < 1 || seatNumber > room.capacity)
    bad('座位号超出考场容量')
  if (
    registrations.some(
      (reg) =>
        reg.id !== row.id &&
        reg.sessionId === row.sessionId &&
        seating.get(reg.id)?.roomId === roomId &&
        seating.get(reg.id)?.seatNumber === seatNumber,
    )
  ) {
    conflict('SEAT_TAKEN', '该座位已分配，请选择其他座位')
  }
  seating.set(row.id, { roomId, seatNumber, ticketNumber: 'ZK-' + row.registrationNumber })
}

function validScore(score) {
  return (
    typeof score === 'number' &&
    Number.isFinite(score) &&
    score >= 0 &&
    score <= 100 &&
    Math.abs(Math.round(score * 100) - score * 100) < 1e-8
  )
}

function writeScore(row, score) {
  if (scorePublication.has(row.sessionId)) conflict('STATE_CONFLICT', '成绩已发布')
  if (!validScore(score)) bad('分数须在 0 到 100 之间，最多两位小数')
  scores.set(row.id, { score, updatedAt: new Date().toISOString() })
}

async function readImport(body) {
  const file = body.get('file')
  if (!file || !file.name.toLowerCase().endsWith('.csv') || file.size > 1024 * 1024)
    bad('请选择 1 MB 以内的 CSV 文件')
  const lines = (await file.text())
    .replace(/^\uFEFF/, '')
    .trim()
    .split(/\r?\n/)
  if (lines.shift() !== 'registrationNumber,score' || !lines.length || lines.length > 5000)
    bad('模板列不匹配或数据行数不在 1–5000 之间')
  return lines
}

function importScores(lines, rows, session) {
  if (scorePublication.has(session.id)) conflict('STATE_CONFLICT', '成绩已发布')
  const errors = []
  const seen = new Set()
  let applied = 0
  lines.forEach((line, index) => {
    const [registrationNumber, value, ...extra] = line.split(',').map((cell) => cell.trim())
    const row = rows.find((reg) => reg.registrationNumber === registrationNumber)
    let message
    if (!row) message = '报名编号无效或不在该场次授权范围内'
    else if (seen.has(registrationNumber)) message = '报名编号在文件中重复'
    else if (
      extra.length ||
      !/^\d{1,3}(\.\d{1,2})?$/.test(value ?? '') ||
      !validScore(Number(value))
    )
      message = '成绩格式无效'
    seen.add(registrationNumber)
    if (message) errors.push({ row: index + 2, message })
    else {
      writeScore(row, Number(value))
      applied += 1
    }
  })
  record('导入成绩', session.name, byId(sites, session.siteId).name)
  return { applied, failed: errors.length, errors: errors.slice(0, 100) }
}

async function sessionAction(sessionId, action, method, body) {
  const rules = {
    'arrangement-runs': ['POST', 'arrangements.write'],
    'ticket-publication': ['PUT', 'tickets.publish'],
    'score-import-template': ['GET', 'scores.import'],
    'score-imports': ['POST', 'scores.import'],
    'score-publication': ['PUT', 'scores.publish'],
  }
  if (!rules[action] || method !== rules[action][0]) missing()
  requirePermission(rules[action][1])
  const session = writableSession(byId(sessions, sessionId))
  if (!publicSession(session)) missing()
  // File reads yield to the event loop, so recheck the current grant before applying the import.
  const lines = action === 'score-imports' ? await readImport(body) : null
  requirePermission(rules[action][1])
  writableSession(session)
  const rows = registrationRows({ sessionId, status: 'REGISTERED' })
  const scope = byId(sites, session.siteId).name
  if (action === 'arrangement-runs') {
    if (ticketPublication.has(sessionId)) conflict('STATE_CONFLICT', '准考证已开放，排考已锁定')
    const unassigned = rows.filter((row) => !seating.has(row.id))
    const occupied = new Set(
      rows
        .map((row) => seating.get(row.id))
        .filter(Boolean)
        .map((seat) => seat.roomId + ':' + seat.seatNumber),
    )
    const available = roomRows(session).flatMap((room) =>
      Array.from({ length: room.capacity }, (_, index) => ({
        roomId: room.id,
        seatNumber: index + 1,
      })).filter((seat) => !occupied.has(seat.roomId + ':' + seat.seatNumber)),
    )
    if (available.length < unassigned.length) conflict('INSUFFICIENT_SEATS', '可用座位不足')
    unassigned.forEach((row, index) =>
      assign(row, available[index].roomId, available[index].seatNumber),
    )
    record('自动排考', session.name, scope)
    return { id: id(), assigned: unassigned.length }
  }
  if (action === 'score-import-template')
    return new Blob(['\uFEFFregistrationNumber,score\r\n'], { type: 'text/csv;charset=utf-8' })
  if (action === 'score-imports') return importScores(lines, rows, session)
  if (body.published !== true) bad('发布参数无效')
  if (action === 'ticket-publication' && !ticketPublication.has(sessionId)) {
    if (!rows.length || rows.some((row) => !seating.has(row.id)))
      conflict('ARRANGEMENT_INCOMPLETE', '请先完成该场次的排考')
    ticketPublication.add(sessionId)
    record('开放准考证', session.name, scope)
  }
  if (action === 'score-publication' && !scorePublication.has(sessionId)) {
    if (!rows.length || rows.some((row) => !scores.has(row.id)))
      conflict('SCORES_INCOMPLETE', '请先录入该场次的全部成绩')
    scorePublication.add(sessionId)
    record('发布成绩', session.name, scope)
  }
  return { published: true }
}

function adminContent(parts, method, query, body) {
  if (!identity().roles.some((role) => role === 'EXAM_STAFF' || role === 'ADMIN')) forbidden()
  const [resource, recordId, action] = parts
  if (resource === 'options' && method === 'GET') return lookup(query)
  if (resource === 'dashboard' && method === 'GET') return dashboard()
  if (resource === 'roles' && method === 'GET') {
    requirePermission('roles.read')
    return pageOf(roleCatalog, query)
  }
  if (resource === 'registrations' && method === 'GET') return adminRegistrations(recordId, query)
  if (
    resource === 'registrations' &&
    method === 'PUT' &&
    ['arrangement', 'score'].includes(action)
  ) {
    requirePermission(action === 'arrangement' ? 'arrangements.write' : 'scores.write')
    const row = byId(registrationRows({ status: 'REGISTERED' }), recordId)
    if (action === 'arrangement') assign(row, body.roomId, body.seatNumber)
    else writeScore(row, body.score)
    record(
      action === 'arrangement' ? '分配座位' : '录入成绩',
      row.registrationNumber,
      byId(sites, row.siteId).name,
    )
    return { id: row.id }
  }
  if ((resource === 'arrangements' || resource === 'scores') && method === 'GET')
    return workbench(resource, query)
  if (resource === 'exam-sessions' && action) return sessionAction(recordId, action, method, body)
  return adminResource(resource, recordId, method, query, body)
}

export async function mockRequest(path, { method = 'GET', query = {}, body, signal } = {}) {
  signal?.throwIfAborted()
  const [first, ...rest] = path.slice(1).split('/').map(decodeURIComponent)
  if (first === 'admin')
    return structuredCloneUnlessBlob(await adminContent(rest, method, query, body))
  const [sessionId, action] = rest
  if (
    path === '/schools' ||
    path === '/me/candidate-profile' ||
    path === '/me/registrations' ||
    (first === 'exam-sessions' && ['registrations', 'registration-options'].includes(action))
  ) {
    return structuredClone(candidateContent(path, { method, query, body, sessionId, action }))
  }
  if (['notices', 'sites', 'exam-sessions'].includes(first) && method === 'GET')
    return structuredCloneUnlessBlob(publicContent([first, ...rest], query))
  return accounts(path, method, body)
}

function structuredCloneUnlessBlob(value) {
  return value instanceof Blob ? value : structuredClone(value)
}
