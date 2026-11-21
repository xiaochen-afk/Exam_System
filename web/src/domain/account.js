/** Keep the login decision and the protected-route guard on the same role contract. */
export function isStaffUser(user) {
  return user?.roles.some((role) => role === 'EXAM_STAFF' || role === 'ADMIN') ?? false
}

export function validateAccount(form, registering = false) {
  if (!form.identifier.trim() || !form.password) return '请填写登录账号和密码'
  if (!registering) return ''
  if (!/^[A-Za-z0-9._-]{4,32}$/.test(form.identifier.trim()))
    return '账号须为 4–32 位字母、数字、点、下划线或连字符'
  if (
    form.password.length < 8 ||
    form.password.length > 64 ||
    !/[A-Za-z]/.test(form.password) ||
    !/\d/.test(form.password)
  ) {
    return '密码须为 8–64 位且包含字母和数字'
  }
  if (form.password !== form.confirmPassword) return '两次输入的密码不一致'
  if (!form.displayName.trim()) return '请填写姓名'
  return ''
}
