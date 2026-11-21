package com.examsystem.portal.controller;

import com.examsystem.portal.service.NoticeAttachmentStorage;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 管理和公开下载使用同一响应格式，始终以附件下载。 */
final class NoticeDownloads {
    private NoticeDownloads() {}

    static ResponseEntity<Resource> response(NoticeAttachmentStorage.Download file) {
        return ResponseEntity.ok()
                .contentType(mediaType(file.mediaType()))
                .contentLength(file.length())
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(file.content());
    }

    private static MediaType mediaType(String storedType) {
        try {
            MediaType type = MediaType.parseMediaType(storedType);
            return type.isConcrete() ? type : MediaType.APPLICATION_OCTET_STREAM;
        } catch (InvalidMediaTypeException error) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
