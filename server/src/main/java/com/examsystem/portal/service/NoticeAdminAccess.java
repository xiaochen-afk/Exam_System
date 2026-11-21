package com.examsystem.portal.service;

import com.examsystem.auth.service.StaffAccessService;
import org.springframework.stereotype.Component;

/** 官方资讯动作必须来自主办方的目录授权。写请求在事务内锁定账号后重新读取授权。 */
@Component
public class NoticeAdminAccess {
    public static final String READ = "notices.read";
    public static final String WRITE = "notices.write";
    public static final String ATTACHMENTS = "notices.attachments.write";
    public static final String PUBLISH = "notices.publish";

    private final StaffAccessService staff;

    public NoticeAdminAccess(StaffAccessService staff) {
        this.staff = staff;
    }

    public void requireReader() {
        staff.reader().requireCatalog(READ);
    }

    public long requireWriter(String action) {
        var actor = staff.writer();
        actor.requireCatalog(action);
        return actor.id();
    }
}
