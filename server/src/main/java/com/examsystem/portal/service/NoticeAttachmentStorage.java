package com.examsystem.portal.service;

import com.examsystem.common.error.ApiException;
import com.examsystem.portal.assembler.PortalResponseAssembler;
import com.examsystem.portal.entity.NoticeAttachment;
import com.examsystem.portal.validation.NoticeFileType;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 附件落在私有目录。业务事务提交的元数据决定文件能否通过接口下载。 */
@Service
public class NoticeAttachmentStorage {
    private static final Logger LOG = LoggerFactory.getLogger(NoticeAttachmentStorage.class);
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private final Path root;

    public NoticeAttachmentStorage(
            @Value("${app.portal.attachment-root:./storage/notice-attachments}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /** 同时检查规范路径和真实路径，拒绝绝对路径、目录穿越及指向目录外的符号链接。 */
    public Download load(NoticeAttachment file) {
        try {
            if (!file.getStorageKey().matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}")) {
                return unavailable(file.getId());
            }
            Path key = Path.of(file.getStorageKey());
            Path candidate = root.resolve(key).normalize();
            if (key.isAbsolute() || !candidate.startsWith(root)) {
                return unavailable(file.getId());
            }
            Path realRoot = root.toRealPath();
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(realRoot)
                    || !Files.isRegularFile(realFile)
                    || !Files.isReadable(realFile)
                    || Files.size(realFile) != file.getByteCount()) {
                return unavailable(file.getId());
            }
            return new Download(
                    new FileSystemResource(realFile),
                    PortalResponseAssembler.safeFilename(file.getFileName()),
                    file.getMediaType(),
                    file.getByteCount());
        } catch (IOException | InvalidPathException | SecurityException error) {
            return unavailable(file.getId());
        }
    }

    /** 回滚清理新文件。事务结果未知时保留文件，避免删除可能已经提交的附件。 */
    public NoticeAttachment store(long noticeId, MultipartFile upload) {
        if (upload.isEmpty()) throw ApiException.invalid("请选择非空附件");
        if (upload.getSize() > MAX_UPLOAD_BYTES) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_TOO_LARGE", "单个附件最多 10 MB");
        }
        String filename = upload.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.length() > 255) {
            throw ApiException.invalid("附件名称须为 1–255 字符");
        }
        filename = PortalResponseAssembler.safeFilename(filename);
        try (var input = new BufferedInputStream(upload.getInputStream())) {
            input.mark(8);
            NoticeFileType type = NoticeFileType.detect(filename, input.readNBytes(8));
            input.reset();
            Files.createDirectories(root);
            Path file = Files.createTempFile(root.toRealPath(), "notice-", ".upload");
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK) removeFile(file);
                        }
                    });
            long size = Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            if (size != upload.getSize() || size > MAX_UPLOAD_BYTES) {
                throw ApiException.invalid("附件大小与上传内容不一致");
            }
            NoticeAttachment row = new NoticeAttachment();
            row.setNoticeId(noticeId);
            row.setFileName(filename);
            row.setStorageKey(file.getFileName().toString());
            row.setMediaType(type.mediaType());
            row.setByteCount(size);
            return row;
        } catch (IOException | SecurityException error) {
            LOG.error(
                    "event=notice_attachment_store_failed exceptionType={}",
                    error.getClass().getName());
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "ATTACHMENT_STORE_FAILED", "附件保存失败，请重试");
        }
    }

    /** 本模块生成的平面文件可安全清理。历史存储键的物理文件留待运维核对。 */
    public void removeAfterCommit(NoticeAttachment file) {
        if (!file.getStorageKey().matches("notice-[0-9]+[.]upload")) return;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        removeFile(root.resolve(file.getStorageKey()));
                    }
                });
    }

    private static void removeFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException error) {
            LOG.warn(
                    "event=notice_attachment_cleanup_failed exceptionType={}",
                    error.getClass().getName());
        }
    }

    private Download unavailable(long id) {
        // 对外不区分缺失、越界和元数据不符，不把文件路径或数据库存储键写入日志。
        LOG.warn("event=public_attachment_unavailable attachmentId={}", id);
        throw ApiException.notFound();
    }

    public record Download(Resource content, String filename, String mediaType, long length) {}
}
