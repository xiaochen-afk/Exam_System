package com.examsystem.exam.service;

import com.examsystem.auth.service.StaffAccessService;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiException;
import com.examsystem.common.query.PageQuery;
import com.examsystem.exam.assembler.ExamResponseAssembler;
import com.examsystem.exam.dto.ExamResponses;
import com.examsystem.exam.mapper.ExamReadMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 关联目录与表单选项；条目/总数在同一只读快照中应用相同授权条件。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ExamQueryService {
    private final ExamReadMapper reads;
    private final StaffAccessService access;

    public ExamQueryService(ExamReadMapper reads, StaffAccessService access) {
        this.reads = reads;
        this.access = access;
    }

    public PageResponse<ExamResponses.Site> sites(
            int page, int size, String keyword, String status) {
        var actor = access.reader();
        actor.requireAction("sites.read");
        var query = new PageQuery(page, size, keyword);
        ExamCatalogService.filterStatus(status);
        return query.response(
                reads
                        .sites(actor.id(), query.keyword(), status, query.size(), query.offset())
                        .stream()
                        .map(ExamResponseAssembler::site)
                        .toList(),
                reads.siteCount(actor.id(), query.keyword(), status));
    }

    public PageResponse<ExamResponses.Room> rooms(
            int page, int size, String keyword, String status) {
        var actor = access.reader();
        actor.requireAction("rooms.read");
        var query = new PageQuery(page, size, keyword);
        ExamCatalogService.filterStatus(status);
        return query.response(
                reads
                        .rooms(actor.id(), query.keyword(), status, query.size(), query.offset())
                        .stream()
                        .map(ExamResponseAssembler::room)
                        .toList(),
                reads.roomCount(actor.id(), query.keyword(), status));
    }

    /** 仅有基础名称与项目 ID，便于创建后由管理员显式配置项目授权。 */
    public PageResponse<ExamResponses.Project> projects(int page, int size, String keyword) {
        var actor = access.reader();
        actor.requireCatalog("projects.create");
        var query = new PageQuery(page, size, keyword);
        return query.response(
                reads.projects(actor.id(), query.keyword(), query.size(), query.offset()).stream()
                        .map(ExamResponseAssembler::project)
                        .toList(),
                reads.projectCount(actor.id(), query.keyword()));
    }

    public List<ExamResponses.Option> options(String context, String kind) {
        var actor = access.reader();
        return switch (context + "/" + kind) {
            case "projects/subjects" -> {
                actor.requireCatalog("projects.create");
                yield reads.subjectOptions();
            }
            case "sites/schools" -> {
                actor.requireCatalog("sites.manage");
                yield reads.schoolOptions();
            }
            case "rooms/sites" -> {
                actor.requireAction("rooms.write");
                yield reads.siteOptions(actor.id(), "rooms");
            }
            case "sessions/projects" -> {
                actor.requireAction("sessions.write");
                yield reads.projectOptions(actor.id());
            }
            case "sessions/sites" -> {
                actor.requireAction("sessions.write");
                yield reads.siteOptions(actor.id(), "sessions");
            }
            default -> throw ApiException.invalid("该表单选项尚未支持");
        };
    }
}
