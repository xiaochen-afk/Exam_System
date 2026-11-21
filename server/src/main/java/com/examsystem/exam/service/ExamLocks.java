package com.examsystem.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.catalog.entity.School;
import com.examsystem.catalog.mapper.SchoolMapper;
import com.examsystem.common.error.ApiException;
import com.examsystem.exam.entity.ExamProject;
import com.examsystem.exam.entity.ExamRoom;
import com.examsystem.exam.entity.ExamSession;
import com.examsystem.exam.entity.ExamSite;
import com.examsystem.exam.entity.ExamSubject;
import com.examsystem.exam.mapper.ExamProjectMapper;
import com.examsystem.exam.mapper.ExamRoomMapper;
import com.examsystem.exam.mapper.ExamSessionMapper;
import com.examsystem.exam.mapper.ExamSiteMapper;
import com.examsystem.exam.mapper.ExamSubjectMapper;
import org.springframework.stereotype.Component;

/** 仅事务内使用的类型化行锁；统一账号后按学校、科目、项目、考点、关系、场次/考场排序。 */
@Component
public class ExamLocks {
    private final SchoolMapper schools;
    private final ExamSubjectMapper subjects;
    private final ExamProjectMapper projects;
    private final ExamSiteMapper sites;
    private final ExamSessionMapper sessions;
    private final ExamRoomMapper rooms;

    public ExamLocks(
            SchoolMapper schools,
            ExamSubjectMapper subjects,
            ExamProjectMapper projects,
            ExamSiteMapper sites,
            ExamSessionMapper sessions,
            ExamRoomMapper rooms) {
        this.schools = schools;
        this.subjects = subjects;
        this.projects = projects;
        this.sites = sites;
        this.sessions = sessions;
        this.rooms = rooms;
    }

    public School school(long id) {
        return found(
                schools.selectOne(
                        Wrappers.<School>lambdaQuery().eq(School::getId, id).last("FOR UPDATE")));
    }

    public ExamSubject subject(long id) {
        return found(
                subjects.selectOne(
                        Wrappers.<ExamSubject>lambdaQuery()
                                .eq(ExamSubject::getId, id)
                                .last("FOR UPDATE")));
    }

    public ExamProject project(long id) {
        return found(
                projects.selectOne(
                        Wrappers.<ExamProject>lambdaQuery()
                                .eq(ExamProject::getId, id)
                                .last("FOR UPDATE")));
    }

    public ExamSite site(long id) {
        return found(
                sites.selectOne(
                        Wrappers.<ExamSite>lambdaQuery()
                                .eq(ExamSite::getId, id)
                                .last("FOR UPDATE")));
    }

    public ExamSession session(long id) {
        return found(
                sessions.selectOne(
                        Wrappers.<ExamSession>lambdaQuery()
                                .eq(ExamSession::getId, id)
                                .last("FOR UPDATE")));
    }

    public ExamRoom room(long id) {
        return found(
                rooms.selectOne(
                        Wrappers.<ExamRoom>lambdaQuery()
                                .eq(ExamRoom::getId, id)
                                .last("FOR UPDATE")));
    }

    private static <T> T found(T value) {
        if (value == null) throw ApiException.notFound();
        return value;
    }
}
