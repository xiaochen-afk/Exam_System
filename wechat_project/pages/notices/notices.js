const { catalogPage } = require('../../utils/catalog-page')
const service = require('../../utils/exam-service')
const format = require('../../utils/format')

Page(catalogPage([["","全部"],["NOTICE","考务通知"],["POLICY","政策法规"],["GUIDE","报考须知"],["FAQ","常见答疑"]], 'category', service.listNotices, format.notice))
