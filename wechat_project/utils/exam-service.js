const { ApiError } = require('./api')
const { UI_PREVIEW } = require('./preview-config')
const account = require('./account')
const preview = require('./preview-data')

const idPath = encodeURIComponent

/** REST 描述符集中在此。联调时由接入层消费 method/path/data/authenticated。 */
function route(method, path, data, authenticated = false) {
  return { method, path, data, authenticated }
}

async function load(operation, args, endpoint) {
  if (endpoint.authenticated) account.requireAccount()
  if (UI_PREVIEW) return preview[operation](...args)
  throw new ApiError('SERVICE_PENDING', '此业务接口待接入')
}

const listExams = (query) => load('listExams', [query], route('GET', '/exam-sessions', query))
const getExam = (id) => load('getExam', [id], route('GET', '/exam-sessions/' + idPath(id)))
const listNotices = (query) => load('listNotices', [query], route('GET', '/notices', query))
const getNotice = (id) => load('getNotice', [id], route('GET', '/notices/' + idPath(id)))
const openNoticeAttachment = (noticeId, attachmentId) => load(
  'openNoticeAttachment', [], route('GET', '/notices/' + idPath(noticeId) + '/attachments/' + idPath(attachmentId))
)
const getProfile = () => load('getProfile', [], route('GET', '/me/candidate-profile', undefined, true))
const updateProfile = (data) => load('updateProfile', [data], route('PUT', '/me/candidate-profile', data, true))
const listSchools = (query) => load('listSchools', [query], route('GET', '/schools', query, true))
const registrationOptions = (id) => load('registrationOptions', [id], route('GET', '/exam-sessions/' + idPath(id) + '/registration-options', undefined, true))
const submitRegistration = (id) => load('submitRegistration', [id], route('POST', '/exam-sessions/' + idPath(id) + '/registrations', {}, true))
const listRegistrations = (query) => load('listRegistrations', [query], route('GET', '/me/registrations', query, true))
const getRegistration = (id) => load('getRegistration', [id], route('GET', '/me/registrations/' + idPath(id), undefined, true))
const getTicket = (id) => load('getTicket', [id], route('GET', '/me/registrations/' + idPath(id) + '/admission-ticket', undefined, true))
const listScores = (query) => load('listScores', [query], route('GET', '/me/scores', query, true))
const listMessages = (query) => load('listMessages', [query], route('GET', null, query, true))

module.exports = {
  listExams, getExam, listNotices, getNotice, openNoticeAttachment, getProfile, updateProfile, listSchools,
  registrationOptions, submitRegistration, listRegistrations, getRegistration, getTicket,
  listScores, listMessages
}
