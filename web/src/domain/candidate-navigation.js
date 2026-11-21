export function candidateReturnQuery(route) {
  if (route.name === 'exam-application') return { exam: route.params.id }
  if (route.name === 'candidate-registrations') return { resume: 'registrations' }
  return {}
}

export function candidateReturnRoute(query) {
  if (typeof query.exam === 'string' && query.exam.length <= 128 && query.exam.trim()) {
    return { name: 'exam-application', params: { id: query.exam } }
  }
  return query.resume === 'registrations' ? { name: 'candidate-registrations' } : '/account'
}
