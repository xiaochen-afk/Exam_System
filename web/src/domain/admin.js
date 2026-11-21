import {
  HomeFilled,
  Reading,
  Location,
  Calendar,
  User,
  Grid,
  DataAnalysis,
  Avatar,
  Key,
  Document,
} from '@element-plus/icons-vue'

export const adminMenu = [
  { key: 'dashboard', title: '工作台', icon: HomeFilled },
  { key: 'notices', title: '资讯管理', icon: Document, permission: 'notices.read' },
  { key: 'subjects', title: '科目管理', icon: Reading, permission: 'subjects.read' },
  { key: 'schools', title: '学校目录', icon: Reading, permission: 'sites.manage' },
  { key: 'sites', title: '考点考场', icon: Location, permission: 'sites.read' },
  { key: 'projects', title: '报考项目', icon: Calendar, permission: 'projects.create' },
  { key: 'sessions', title: '考试场次', icon: Calendar, permission: 'sessions.read' },
  { key: 'registrations', title: '报名管理', icon: User, permission: 'registrations.read' },
  { key: 'arrangements', title: '排考管理', icon: Grid, permission: 'arrangements.read' },
  { key: 'scores', title: '成绩管理', icon: DataAnalysis, permission: 'scores.read' },
  { key: 'users', title: '内部账号', icon: Avatar, permission: 'users.read' },
  { key: 'roles', title: '角色与权限', icon: Key, permission: 'roles.read' },
  { key: 'logs', title: '审计日志', icon: Document, permission: 'logs.read' },
]

const field = (key, label, type = 'text', options = {}) => ({ key, label, type, ...options })
const description = field('description', '说明', 'textarea', { optional: true, max: 2000 })
const enabled = field('status', '状态', 'select', {
  choices: { ACTIVE: '启用', INACTIVE: '停用' },
  initial: 'ACTIVE',
})
const name = field('name', '名称', 'text', { max: 100 })
const capacity = field('capacity', '容量', 'number', { min: 1, max: 100000, step: 1 })
const date = (key, label) => field(key, label, 'datetime-local')
const relation = (key, label, lookup) => field(key, label, 'select', { lookup })
const multi = (key, label, lookup, optional = false) =>
  field(key, label, 'multiselect', { lookup, optional })
const label = (key, title) => [key, title]

export const resources = {
  schools: {
    path: '/schools',
    singular: '学校',
    summary: '维护稳定学校目录，用于承办学校关联及在校考生本校推荐',
    columns: [label('code', '学校代码'), label('name', '学校名称'), label('status', '状态')],
    fields: [field('code', '学校代码', 'text', { max: 32 }), name, enabled],
    create: 'sites.manage',
    edit: 'sites.manage',
  },
  projects: {
    path: '/exam-projects',
    singular: '报考项目',
    summary:
      '先建立项目，再由管理员按项目 ID 分配场次维护、发布等动作。相同项目的各考点共享一次报考机会。',
    columns: [
      label('id', '项目 ID'),
      label('subjectName', '科目'),
      label('applicationCode', '报考项目代码'),
    ],
    fields: [
      relation('subjectId', '科目', 'subjects'),
      field('applicationCode', '报考项目代码（选填）', 'text', {
        optional: true,
        max: 32,
        hint: '跨考点备选场次共用该项目。代码留空时，该项目只能创建一个场次。',
      }),
    ],
    filterStatus: false,
    create: 'projects.create',
  },
  subjects: {
    path: '/subjects',
    singular: '科目',
    summary: '维护考试科目、代码与启用状态',
    columns: [
      label('code', '科目代码'),
      label('name', '科目名称'),
      label('description', '说明'),
      label('status', '状态'),
    ],
    fields: [
      field('code', '科目代码', 'text', { max: 32 }),
      field('name', '科目名称'),
      description,
      enabled,
    ],
    create: 'subjects.write',
    edit: 'subjects.write',
  },
  sites: {
    path: '/sites',
    singular: '考点',
    summary: '管理考点资格及授权范围内的考场',
    columns: [
      label('name', '考点名称'),
      label('schoolName', '承办学校'),
      label('address', '地址'),
      label('qualificationStatus', '考点资格'),
      label('status', '启用状态'),
    ],
    fields: [
      name,
      { ...relation('schoolId', '承办学校（选填）', 'schools'), optional: true },
      field('address', '地址', 'text', { max: 200 }),
      field('qualificationStatus', '考点资格', 'select', {
        choices: { UNQUALIFIED: '尚未取得资格', QUALIFIED: '已具备资格' },
        initial: 'UNQUALIFIED',
      }),
      { ...enabled, label: '启用状态' },
    ],
    create: 'sites.manage',
    edit: 'sites.write',
  },
  rooms: {
    path: '/rooms',
    singular: '考场',
    summary: '维护考点下的考场和可用座位',
    columns: [
      label('name', '考场名称'),
      label('siteName', '所属考点'),
      label('capacity', '座位数'),
      label('status', '状态'),
    ],
    fields: [
      { ...relation('siteId', '所属考点', 'sites'), immutable: true },
      name,
      capacity,
      enabled,
    ],
    create: 'rooms.write',
    edit: 'rooms.write',
  },
  sessions: {
    path: '/exam-sessions',
    singular: '场次',
    summary: '设置科目、报考时间、考点与名额，发布考试场次',
    columns: [
      label('name', '考试项目'),
      label('subjectName', '考试科目'),
      label('siteName', '考点'),
      label('registrationStartsAt', '报名开始'),
      label('registrationEndsAt', '报名截止'),
      label('examStartsAt', '考试时间'),
      label('status', '状态'),
      label('registrationOpen', '考点报名开关'),
    ],
    fields: [
      field('name', '考试项目'),
      {
        ...relation('projectId', '报考项目', 'projects'),
        immutable: true,
        hint: '仅可选择已授予项目级场次维护权限的项目，创建后固定。',
      },
      { ...relation('siteId', '考点', 'sites'), immutable: true },
      { ...date('registrationStartsAt', '报名开始'), lockAfterRegistration: true },
      { ...date('registrationEndsAt', '报名截止'), lockAfterRegistration: true },
      { ...date('examStartsAt', '考试开始'), lockAfterRegistration: true },
      { ...date('examEndsAt', '考试结束'), lockAfterRegistration: true },
      { ...capacity, label: '人数上限' },
      description,
    ],
    create: 'sessions.write',
    edit: 'sessions.write',
    publish: 'sessions.publish',
  },
  users: {
    path: '/users',
    singular: '内部账号',
    summary: '开通、停用账号，配置角色及考试与考点授权',
    columns: [
      label('identifier', '登录账号'),
      label('displayName', '姓名'),
      label('roleLabel', '角色'),
      label('scopeLabel', '数据范围'),
      label('status', '状态'),
    ],
    fields: [
      field('identifier', '登录账号', 'text', { max: 128, createOnly: true }),
      field('displayName', '姓名', 'text', { max: 50 }),
      field('initialPassword', '初始密码', 'password', { createOnly: true, minLength: 8, max: 64 }),
      multi('roleCodes', '角色', 'roles'),
      multi('siteIds', '授权考点', 'sites', true),
      multi('sessionIds', '授权场次', 'sessions', true),
      enabled,
    ],
    create: 'users.write',
    edit: 'users.write',
  },
  logs: {
    path: '/audit-logs',
    summary: '查询关键操作、授权范围与执行结果',
    columns: [
      label('occurredAt', '操作时间'),
      label('operatorName', '操作者'),
      label('action', '动作'),
      label('targetLabel', '对象'),
      label('scopeLabel', '范围'),
      label('result', '结果'),
    ],
  },
}

export function validateSession(form, row) {
  const { registrationStartsAt, registrationEndsAt, examStartsAt, examEndsAt, capacity } = form
  if (!(
    registrationStartsAt < registrationEndsAt &&
    registrationEndsAt < examStartsAt &&
    examStartsAt < examEndsAt
  )) {
    return '请按报名开始、报名截止、考试开始、考试结束的顺序设置时间'
  }
  if (row && capacity < row.registrationCount) return '人数上限不能少于已报名人数'
  return ''
}
