package com.examsystem.portal.controller;

import com.examsystem.common.dto.PageResponse;
import com.examsystem.portal.dto.PortalQueries;
import com.examsystem.portal.dto.PortalResponses;
import com.examsystem.portal.service.NoticeService;
import com.examsystem.portal.service.PublicExamService;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 匿名门户只读入口；发布筛选在服务层执行，下载始终受父资讯可见性和文件归属约束。 */
@RestController
@RequestMapping("/api/v1")
public class PublicPortalController {
    private final NoticeService notices;
    private final PublicExamService exams;

    public PublicPortalController(NoticeService notices, PublicExamService exams) {
        this.notices = notices;
        this.exams = exams;
    }

    // region 新闻政策与受控附件

    /** 支持首页焦点、分类列表和下载中心；缺省布尔参数不附加筛选条件。 */
    @GetMapping("/notices")
    PageResponse<PortalResponses.NoticeItem> notices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) PortalQueries.NoticeCategory category,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) Boolean hasAttachments) {
        return notices.list(
                new PortalQueries.Notices(page, size, keyword, category, featured, hasAttachments));
    }

    @GetMapping("/notices/{id}")
    PortalResponses.NoticeDetail notice(@PathVariable @Positive long id) {
        return notices.detail(id);
    }

    /** 强制附件下载并使用 UTF-8 文件名；浏览器不能把正文 HTML 附件当成站内页面执行。 */
    @GetMapping("/notices/{id}/attachments/{fileId}")
    ResponseEntity<Resource> attachment(
            @PathVariable @Positive long id, @PathVariable @Positive long fileId) {
        return NoticeDownloads.response(notices.attachment(id, fileId));
    }

    // endregion

    // region 公开场次与考点

    @GetMapping("/exam-sessions")
    PageResponse<PortalResponses.Session> sessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) PortalQueries.RegistrationStatus registrationStatus) {
        return exams.sessions(page, size, keyword, registrationStatus);
    }

    @GetMapping("/exam-sessions/{id}")
    PortalResponses.Session session(@PathVariable @Positive long id) {
        return exams.session(id);
    }

    @GetMapping("/sites")
    PageResponse<PortalResponses.Site> sites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return exams.sites(page, size);
    }

    // endregion

}
