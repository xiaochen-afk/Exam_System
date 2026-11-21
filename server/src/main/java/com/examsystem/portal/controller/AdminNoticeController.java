package com.examsystem.portal.controller;

import com.examsystem.audit.service.AuditService;
import com.examsystem.auth.service.StaffAccessService;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiErrors;
import com.examsystem.common.error.ApiException;
import com.examsystem.portal.dto.AdminNoticeDtos;
import com.examsystem.portal.dto.PortalQueries;
import com.examsystem.portal.model.NoticeStatus;
import com.examsystem.portal.service.AdminNoticeCommandService;
import com.examsystem.portal.service.AdminNoticeQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 官方资讯管理 HTTP 入口。权限和状态写入归服务层，失败审计在写事务回滚后记录。 */
@RestController
@RequestMapping(AdminNoticeController.BASE)
public class AdminNoticeController {
    static final String BASE = "/api/v1/admin/notices";
    private static final Logger LOG = LoggerFactory.getLogger(AdminNoticeController.class);

    private final AdminNoticeQueryService reads;
    private final AdminNoticeCommandService commands;
    private final StaffAccessService staff;
    private final AuditService audit;

    public AdminNoticeController(
            AdminNoticeQueryService reads,
            AdminNoticeCommandService commands,
            StaffAccessService staff,
            AuditService audit) {
        this.reads = reads;
        this.commands = commands;
        this.staff = staff;
        this.audit = audit;
    }

    // region 查询

    @GetMapping
    PageResponse<AdminNoticeDtos.Item> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) PortalQueries.NoticeCategory category,
            @RequestParam(required = false) NoticeStatus status) {
        return reads.list(page, size, keyword, category, status);
    }

    @GetMapping("/{id}")
    AdminNoticeDtos.Detail detail(@PathVariable @Positive long id) {
        return reads.detail(id);
    }

    @GetMapping("/{id}/attachments/{fileId}")
    ResponseEntity<Resource> download(
            @PathVariable @Positive long id, @PathVariable @Positive long fileId) {
        return NoticeDownloads.response(reads.attachment(id, fileId));
    }

    // endregion

    // region 内容、发布和附件写入

    @PostMapping
    ResponseEntity<AdminNoticeDtos.Item> create(
            @RequestBody AdminNoticeDtos.Content input, HttpServletRequest request) {
        var row = execute("notices.create", null, request, rid -> commands.create(input, rid));
        return ResponseEntity.created(URI.create(BASE + "/" + row.id())).body(row);
    }

    @PatchMapping("/{id}")
    AdminNoticeDtos.Item update(
            @PathVariable @Positive long id,
            @RequestBody AdminNoticeDtos.Content input,
            HttpServletRequest request) {
        return execute("notices.update", id, request, rid -> commands.update(id, input, rid));
    }

    @PutMapping("/{id}/publication")
    AdminNoticeDtos.Item setPublication(
            @PathVariable @Positive long id,
            @RequestBody AdminNoticeDtos.Publication input,
            HttpServletRequest request) {
        if (input.published() == null) throw ApiException.invalid("请选择发布或下架");
        String action = input.published() ? "notices.publish" : "notices.withdraw";
        return execute(
                action, id, request, rid -> commands.setPublication(id, input.published(), rid));
    }

    @PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AdminNoticeDtos.Attachment> upload(
            @PathVariable @Positive long id,
            @RequestPart MultipartFile file,
            HttpServletRequest request) {
        var row =
                execute(
                        "notices.attachments.upload",
                        id,
                        request,
                        rid -> commands.upload(id, file, rid));
        return ResponseEntity.created(URI.create(BASE + "/" + id + "/attachments/" + row.id()))
                .body(row);
    }

    @DeleteMapping("/{id}/attachments/{fileId}")
    ResponseEntity<Void> remove(
            @PathVariable @Positive long id,
            @PathVariable @Positive long fileId,
            HttpServletRequest request) {
        return execute(
                "notices.attachments.remove",
                id,
                request,
                rid -> {
                    commands.remove(id, fileId, rid);
                    return ResponseEntity.noContent().build();
                });
    }

    // endregion

    private <T> T execute(
            String action, Long target, HttpServletRequest request, Function<String, T> command) {
        String requestId = ApiErrors.requestId(request);
        try {
            return command.apply(requestId);
        } catch (PessimisticLockingFailureException | QueryTimeoutException busy) {
            recordFailure(action, target, "NOTICE_BUSY", requestId);
            throw new ApiException(HttpStatus.CONFLICT, "NOTICE_BUSY", "资讯正在处理，请稍后重试");
        } catch (ApiException rejected) {
            recordFailure(action, target, rejected.code(), requestId);
            throw rejected;
        } catch (RuntimeException error) {
            recordFailure(action, target, "INTERNAL_ERROR", requestId);
            throw error;
        }
    }

    private void recordFailure(String action, Long target, String code, String requestId) {
        try {
            long actorId = staff.reader().id();
            audit.resourceFailure(actorId, action, "NOTICE", target, code, requestId);
        } catch (ApiException ignored) {
            // 未获内部会话的请求由安全层记录，避免把它当成已识别的官方操作者。
        } catch (RuntimeException error) {
            LOG.warn(
                    "event=notice_audit_failed requestId={} exceptionType={}",
                    requestId,
                    error.getClass().getName());
        }
    }
}
