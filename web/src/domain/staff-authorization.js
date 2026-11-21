/** Shared form rules for the real grants[] contract; the server remains the authorization boundary. */
export const scopeLabels = {
  SYSTEM: '系统管理',
  CATALOG: '基础信息目录',
  PROJECT: '指定报考项目',
  SESSION: '指定考试场次',
}

export function scopesForRole(options, roleCode) {
  return [
    ...new Set(
      options?.roles.find((role) => role.id === roleCode)?.scopes.map((scope) => scope.scopeType) ??
        [],
    ),
  ]
}

export function actionsForGrant(options, grant) {
  return (
    options?.roles
      .find((role) => role.id === grant.roleCode)
      ?.scopes.filter(
        (scope) => scope.scopeType === grant.scopeType && scope.permissionCode !== 'admins.manage',
      ) ?? []
  )
}

/** Omitted grants preserve existing authorization; an explicit empty list revokes all grants. */
export function staffPayload({ form, row, grants, replaceGrants, options }) {
  const displayName = form.displayName.trim()
  if (!displayName || displayName.length > 50) throw new Error('姓名须为 1–50 字符')
  if (!['ACTIVE', 'INACTIVE'].includes(form.status)) throw new Error('请选择有效状态')
  const body = { displayName, status: form.status }
  if (!row) {
    const identifier = form.identifier.trim()
    if (!/^[A-Za-z0-9._-]{4,32}$/.test(identifier))
      throw new Error('账号须为 4–32 位字母、数字、点、下划线或连字符')
    if (
      form.initialPassword.length < 8 ||
      form.initialPassword.length > 64 ||
      !/[A-Za-z]/.test(form.initialPassword) ||
      !/[0-9]/.test(form.initialPassword)
    ) {
      throw new Error('初始密码须为 8–64 位，且包含英文字母和数字')
    }
    body.identifier = identifier
    body.initialPassword = form.initialPassword
  }
  if (row && !replaceGrants) return body
  if (grants.length > 100) throw new Error('最多可配置 100 条授权')
  const keys = new Set()
  body.grants = grants.map((grant) => {
    if (!scopesForRole(options, grant.roleCode).includes(grant.scopeType))
      throw new Error('请选择有效的角色和授权范围')
    const projectId = grant.scopeType === 'PROJECT' ? grant.projectId : null
    const sessionId = grant.scopeType === 'SESSION' ? grant.sessionId : null
    if (
      grant.scopeType === 'PROJECT' &&
      !options.projects.some((option) => option.id === projectId)
    )
      throw new Error('请选择报考项目')
    if (
      grant.scopeType === 'SESSION' &&
      !options.sessions.some((option) => option.id === sessionId)
    )
      throw new Error('请选择考试场次')
    const allowed = actionsForGrant(options, grant).map((action) => action.permissionCode)
    const permissionCodes = [...new Set(grant.permissionCodes)]
    if (
      !permissionCodes.length ||
      permissionCodes.some((permission) => !allowed.includes(permission))
    )
      throw new Error('请为每条授权选择允许的动作')
    const key = JSON.stringify([grant.roleCode, grant.scopeType, projectId, sessionId])
    if (keys.has(key)) throw new Error('同一角色与范围不能重复授权')
    keys.add(key)
    return {
      roleCode: grant.roleCode,
      scopeType: grant.scopeType,
      projectId,
      sessionId,
      permissionCodes,
    }
  })
  return body
}
