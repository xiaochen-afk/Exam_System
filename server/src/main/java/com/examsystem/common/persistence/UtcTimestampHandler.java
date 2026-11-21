package com.examsystem.common.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/** 填充明确标注的实体时间字段，统一使用服务端 Clock 的 UTC 时间；不覆盖已提供的值。 */
@Component
public class UtcTimestampHandler implements MetaObjectHandler {
    private final Clock clock;

    public UtcTimestampHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "passwordChangedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "occurredAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(
                metaObject,
                "updatedAt",
                LocalDateTime.class,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
