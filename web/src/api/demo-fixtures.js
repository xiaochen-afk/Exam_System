const publishedAt = (day) => `2026-11-${String(day).padStart(2, '0')}T09:00:00+08:00`

const noticeTitles = {
  POLICY: [
    '关于进一步规范学校各类考试管理工作的实施办法（试行）',
    '考试违规行为认定与处理办法要点解读',
    '关于印发《学校学生考试管理规定》的通知',
    '关于加强考试诚信建设的指导意见',
    '考试安全与保密工作管理办法',
    '关于规范考场管理和监考工作的实施细则',
    '关于严肃考风考纪的若干规定',
  ],
  NOTICE: [
    '2026 年冬季考试报名工作安排',
    '计算机应用能力考试场次安排',
    '考务工作培训与考场准备通知',
  ],
  GUIDE: ['考生网上报名与现场身份核验说明', '考生考前准备清单', '考点选择与报考资格说明'],
  FAQ: [
    '本校考点满额后如何改选其他考点？',
    '在哪里查看准考证和考试成绩？',
    '考试当天应提前多久到达考点？',
  ],
}

const recentDays = [18, 16, 13, 12, 10, 8, 6]
export const notices = Object.entries(noticeTitles).flatMap(([category, titles]) =>
  titles.map((title, index) => {
    const featured = category === 'POLICY' && index === 0
    const summary = featured
      ? '为加强学校考试管理，维护考试公平公正，保障考试安全有序开展，现将报名、考场纪律与考务组织的相关要求予以公布。'
      : '请考生在考试前查阅相关安排，按要求完成报名、身份核验和到场准备。'
    return {
      id: category.toLowerCase() + '-' + (index + 1),
      title,
      category,
      summary,
      publisher: '考试服务中心（演示）',
      featured,
      pinned: index === 0,
      publishedAt: publishedAt(recentDays[index]),
      body:
        summary +
        '\n\n一、请以所报考试的最新公告为准，提前核对考试科目、时间和考点。\n\n二、请按准考证提示携带有效证件，在规定时间到达考场。\n\n三、如有疑问，请参考报考指南与常见答疑。\n\n本文为本地演示内容。',
      attachments:
        category === 'GUIDE' && index === 0
          ? [{ id: 'checklist', filename: '考前准备清单.txt' }]
          : [],
    }
  }),
)

export const subjects = [
  {
    id: 'sub-1',
    code: 'SKILL',
    name: '综合实践能力',
    description: '专业技能综合实践考核',
    status: 'ACTIVE',
  },
  { id: 'sub-2', code: 'ENGLISH', name: '大学英语', description: '英语水平考核', status: 'ACTIVE' },
  {
    id: 'sub-3',
    code: 'COMPUTER',
    name: '计算机基础与应用',
    description: '计算机应用能力考核',
    status: 'ACTIVE',
  },
  {
    id: 'sub-4',
    code: 'TEACH',
    name: '教育教学基础',
    description: '教育教学基础知识考核',
    status: 'ACTIVE',
  },
]

export const sites = [
  {
    id: 'site-1',
    name: '东校区考点',
    schoolName: '示例大学',
    schoolId: 'school-1',
    address: '示例大学东校区教学楼',
    status: 'ACTIVE',
  },
  {
    id: 'site-2',
    name: '西校区考点',
    schoolName: '示例理工学院',
    schoolId: 'school-2',
    address: '示例理工学院西校区实验楼',
    status: 'ACTIVE',
  },
  {
    id: 'site-3',
    name: '南校区考点',
    schoolName: '示例师范学院',
    schoolId: 'school-3',
    address: '示例师范学院南校区综合楼',
    status: 'ACTIVE',
  },
]

export const schools = sites.map(({ schoolName }, index) => ({
  id: 'school-' + (index + 1),
  name: schoolName,
}))

export const rooms = sites.flatMap((site, index) =>
  Array.from({ length: 2 }, (_, roomIndex) => ({
    id: `room-${index + 1}-${roomIndex + 1}`,
    siteId: site.id,
    name: `第 ${roomIndex + 1} 考场`,
    status: 'ACTIVE',
    capacity: 40,
  })),
)

const nextDate = (days, hour = 9) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  date.setHours(hour, 0, 0, 0)
  return date.toISOString()
}

export const sessions = [
  ['专业技能综合测试', 0, 0, 'PUBLISHED', -5, 9, 17, 80, 'SKILL-WINTER'],
  ['大学英语水平考试', 1, 1, 'PUBLISHED', -3, 11, 20, 80],
  ['计算机应用能力考试', 2, 0, 'PUBLISHED', 3, 15, 24, 60],
  ['教师资格考试（校内考点）', 3, 2, 'COMPLETED', -20, -9, -2, 60],
  ['计算机应用能力考试补充场次', 2, 0, 'DRAFT', 8, 18, 27, 60],
  ['专业技能综合测试', 0, 1, 'PUBLISHED', -5, 8, 18, 60, 'SKILL-WINTER'],
].map(
  ([name, subject, site, status, opens, closes, exam, capacity, applicationCode = ''], index) => ({
    id: 'session-' + (index + 1),
    name,
    subjectId: subjects[subject].id,
    siteId: sites[site].id,
    status,
    capacity,
    applicationCode,
    registrationStartsAt: nextDate(opens),
    registrationEndsAt: nextDate(closes, 18),
    examStartsAt: nextDate(exam),
    examEndsAt: nextDate(exam, 11),
    description:
      '请在报名开放期间查看报考资格及考点安排。考试当天请携带有效证件，按准考证所示时间和地点参加考试。',
  }),
)

const familyNames = ['张', '李', '王', '陈', '刘', '黄', '周', '吴', '郑', '赵']
export const registrations = Array.from({ length: 32 }, (_, index) => {
  const session = sessions[index % 2]
  const serial = String(index + 1).padStart(4, '0')
  const student = index % 3 !== 0
  return {
    id: 'reg-' + serial,
    registrationNumber: 'BM-DEMO-' + serial,
    candidateName: familyNames[index % familyNames.length] + '*',
    candidateType: student ? 'STUDENT' : 'SOCIAL',
    schoolName: student ? sites[(index + 1) % sites.length].schoolName : null,
    sessionId: session.id,
    siteId: session.siteId,
    submittedAt: nextDate(-1, 10 + Math.floor(index / 8)),
    status: 'REGISTERED',
  }
})

export const roleCatalog = [
  {
    id: 'ORGANIZER',
    name: '主办方考务',
    description: '在获授范围内统筹考试与考点开放。',
    permissions: [
      'subjects.read',
      'subjects.write',
      'sites.read',
      'sites.write',
      'sites.manage',
      'rooms.read',
      'rooms.write',
      'sessions.read',
      'sessions.write',
      'sessions.publish',
      'registrations.read',
      'arrangements.read',
      'arrangements.write',
      'tickets.publish',
      'scores.read',
      'scores.write',
      'scores.import',
      'scores.publish',
    ],
    permissionLabels: [
      '管理科目、考点与考场',
      '创建和发布考试场次',
      '查看报名并分配座位',
      '开放准考证',
      '录入、导入与发布成绩',
    ],
  },
  {
    id: 'SITE_MANAGER',
    name: '考点负责人',
    description: '在同时获授的考点和考试场次内处理考务。',
    permissions: [
      'sites.read',
      'sites.write',
      'rooms.read',
      'rooms.write',
      'sessions.read',
      'registrations.read',
      'arrangements.read',
      'arrangements.write',
    ],
    permissionLabels: ['维护获准的考点地址和考场', '查看授权场次和实际报考名单', '分配考场与座位'],
  },
  {
    id: 'SYSTEM_ADMIN',
    name: '系统管理员',
    description: '管理内部账号、权限与审计。',
    permissions: ['users.read', 'users.write', 'roles.read', 'logs.read'],
    permissionLabels: ['创建和停用内部账号', '配置角色及范围并撤销授权', '查看脱敏审计日志'],
  },
]

export const users = [
  {
    id: 'staff-1',
    identifier: 'organizer.demo',
    displayName: '主办方考务',
    roleCodes: ['ORGANIZER'],
    siteIds: [],
    sessionIds: [],
    status: 'ACTIVE',
  },
  {
    id: 'staff-2',
    identifier: 'site.demo',
    displayName: '东校区考点负责人',
    roleCodes: ['SITE_MANAGER'],
    siteIds: ['site-1'],
    sessionIds: ['session-1'],
    status: 'ACTIVE',
  },
  {
    id: 'staff-3',
    identifier: 'admin.demo',
    displayName: '系统管理员',
    roleCodes: ['SYSTEM_ADMIN'],
    siteIds: [],
    sessionIds: [],
    status: 'ACTIVE',
  },
]
