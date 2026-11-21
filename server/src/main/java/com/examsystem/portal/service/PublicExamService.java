package com.examsystem.portal.service;

import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiException;
import com.examsystem.common.query.PageQuery;
import com.examsystem.portal.assembler.PortalResponseAssembler;
import com.examsystem.portal.dto.PortalQueries;
import com.examsystem.portal.dto.PortalResponses;
import com.examsystem.portal.mapper.PublicExamMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 匿名考试信息读取；本次请求共用 UTC 时刻，实际报名仍由后续事务重新检查资格与容量。 */
@Service
public class PublicExamService {
    private final PublicExamMapper mapper;
    private final Clock clock;

    public PublicExamService(PublicExamMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /** 列表与总数在只读事务内使用相同窗口与筛选；公开 DTO 不复用内部响应。 */
    @Transactional(readOnly = true)
    public PageResponse<PortalResponses.Session> sessions(
            int page, int size, String keyword, PortalQueries.RegistrationStatus status) {
        PageQuery query = new PageQuery(page, size, keyword);
        LocalDateTime now = now();
        String filter = status == null ? "" : status.name();
        var items =
                mapper.sessions(query.keyword(), filter, now, query.size(), query.offset()).stream()
                        .map(PortalResponseAssembler::session)
                        .toList();
        return query.response(items, mapper.sessionCount(query.keyword(), filter, now));
    }

    /** 草稿和不存在的场次统一 404；暂停开放的已发布场次只提供不可报状态。 */
    @Transactional(readOnly = true)
    public PortalResponses.Session session(long id) {
        var row = mapper.session(id, now());
        if (row == null) throw ApiException.notFound();
        return PortalResponseAssembler.session(row);
    }

    @Transactional(readOnly = true)
    public PageResponse<PortalResponses.Site> sites(int page, int size) {
        PageQuery query = new PageQuery(page, size, "");
        var items =
                mapper.sites(query.size(), query.offset()).stream()
                        .map(PortalResponseAssembler::site)
                        .toList();
        return query.response(items, mapper.siteCount());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
