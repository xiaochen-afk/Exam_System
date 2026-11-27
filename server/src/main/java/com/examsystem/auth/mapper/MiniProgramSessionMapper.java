package com.examsystem.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.auth.entity.MiniProgramSession;
import com.examsystem.auth.model.MiniProgramSessionIdentity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 认证读取同时检查微信身份、账号状态与会话版本。 */
@Mapper
public interface MiniProgramSessionMapper extends BaseMapper<MiniProgramSession> {
    MiniProgramSessionIdentity findActive(
            @Param("digest") byte[] digest,
            @Param("appId") String appId,
            @Param("now") LocalDateTime now);

    default boolean isActive(long sessionId, LocalDateTime now) {
        return exists(
                Wrappers.<MiniProgramSession>lambdaQuery()
                        .eq(MiniProgramSession::getId, sessionId)
                        .isNull(MiniProgramSession::getRevokedAt)
                        .gt(MiniProgramSession::getExpiresAt, now));
    }

    default void revoke(long sessionId, LocalDateTime now) {
        update(
                Wrappers.<MiniProgramSession>lambdaUpdate()
                        .eq(MiniProgramSession::getId, sessionId)
                        .isNull(MiniProgramSession::getRevokedAt)
                        .setSql("revoked_at = GREATEST(issued_at, {0})", now));
    }
}
