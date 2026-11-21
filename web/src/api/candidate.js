import { request } from './client.js'

function candidateRequest(path, options) {
  return request(path, { ...options, session: true })
}

export function getCandidateProfile(signal) {
  return candidateRequest('/me/candidate-profile', { signal })
}

export function saveCandidateProfile(profile) {
  return candidateRequest('/me/candidate-profile', { method: 'PUT', body: profile })
}

export function getSchools(query, signal) {
  return candidateRequest('/schools', { query, signal })
}

function sessionPath(sessionId) {
  return '/exam-sessions/' + encodeURIComponent(sessionId)
}

export function getApplicationOptions(sessionId, signal) {
  return candidateRequest(sessionPath(sessionId) + '/registration-options', { signal })
}

export function submitApplication(sessionId) {
  return candidateRequest(sessionPath(sessionId) + '/registrations', { method: 'POST', body: {} })
}

export function getMyRegistrations(query, signal) {
  return candidateRequest('/me/registrations', { query, signal })
}
