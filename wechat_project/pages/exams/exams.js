const { catalogPage } = require('../../utils/catalog-page')
const service = require('../../utils/exam-service')
const format = require('../../utils/format')

Page(catalogPage([["","全部"],["OPEN","报名中"],["UPCOMING","即将报名"],["CLOSED","已截止"],["FULL","已满"]], 'registrationStatus', service.listExams, format.exam))
