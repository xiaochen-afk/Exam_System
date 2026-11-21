package com.examsystem.portal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.common.error.ApiException;
import com.examsystem.portal.entity.NoticeAttachment;
import org.apache.ibatis.annotations.Mapper;

/** 附件元数据读取。调用方验证父资讯可见性或后台权限后，按父 ID 和附件 ID 查询。 */
@Mapper
public interface NoticeAttachmentMapper extends BaseMapper<NoticeAttachment> {
    default NoticeAttachment requireStoredFile(long noticeId, long fileId) {
        var file =
                selectOne(
                        Wrappers.<NoticeAttachment>lambdaQuery()
                                .select(
                                        NoticeAttachment::getId,
                                        NoticeAttachment::getFileName,
                                        NoticeAttachment::getStorageKey,
                                        NoticeAttachment::getMediaType,
                                        NoticeAttachment::getByteCount)
                                .eq(NoticeAttachment::getId, fileId)
                                .eq(NoticeAttachment::getNoticeId, noticeId));
        if (file == null) throw ApiException.notFound();
        return file;
    }
}
