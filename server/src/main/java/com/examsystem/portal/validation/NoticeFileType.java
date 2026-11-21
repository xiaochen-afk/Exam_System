package com.examsystem.portal.validation;

import com.examsystem.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** 服务端同时核对扩展名和文件头，忽略客户端提供的 MIME 类型。 */
public enum NoticeFileType {
    PDF(List.of(".pdf"), "application/pdf", "%PDF-".getBytes(StandardCharsets.US_ASCII)),
    PNG(
            List.of(".png"),
            "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}),
    JPEG(
            List.of(".jpeg", ".jpg"),
            "image/jpeg",
            new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    private final List<String> extensions;
    private final String mediaType;
    private final byte[] signature;

    NoticeFileType(List<String> extensions, String mediaType, byte[] signature) {
        this.extensions = extensions;
        this.mediaType = mediaType;
        this.signature = signature;
    }

    public String mediaType() {
        return mediaType;
    }

    public static NoticeFileType detect(String filename, byte[] header) {
        String lower = filename.toLowerCase(Locale.ROOT);
        for (var type : values()) {
            if (type.extensions.stream().anyMatch(lower::endsWith) && type.hasSignature(header)) {
                return type;
            }
        }
        throw ApiException.invalid("附件须为有效的 PDF、PNG 或 JPEG 文件");
    }

    private boolean hasSignature(byte[] header) {
        if (header.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) return false;
        }
        return true;
    }
}
