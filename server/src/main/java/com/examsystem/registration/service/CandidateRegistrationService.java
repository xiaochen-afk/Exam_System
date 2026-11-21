package com.examsystem.registration.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiException;
import com.examsystem.common.query.PageQuery;
import com.examsystem.registration.assembler.RegistrationResponseAssembler;
import com.examsystem.registration.dto.RegistrationResponses;
import com.examsystem.registration.entity.Registration;
import com.examsystem.registration.mapper.EnrollmentMapper;
import com.examsystem.registration.mapper.RegistrationMapper;
import com.examsystem.registration.mapper.RegistrationReadMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 考生本人选项、记录与回执查询；身份不取自请求，跨项目和他人记录不能混入。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CandidateRegistrationService {
    private final CandidateAccess access;
    private final CandidateProfileService profiles;
    private final RegistrationReadMapper reads;
    private final RegistrationMapper registrations;
    private final EnrollmentMapper enrollment;

    public CandidateRegistrationService(
            CandidateAccess access,
            CandidateProfileService profiles,
            RegistrationReadMapper reads,
            RegistrationMapper registrations,
            EnrollmentMapper enrollment) {
        this.access = access;
        this.profiles = profiles;
        this.reads = reads;
        this.registrations = registrations;
        this.enrollment = enrollment;
    }

    /** 同一稳定项目的已公开场次；学校只做推荐，允许主动选择其他可报考点。 */
    public RegistrationResponses.Options options(long sourceSessionId) {
        long candidate = access.current().id();
        Long project = reads.publicProject(sourceSessionId);
        if (project == null) throw ApiException.notFound();
        var profile = profiles.state(candidate);
        var registered = reads.ownProject(candidate, project);
        var receipt =
                registered == null ? null : RegistrationResponseAssembler.candidate(registered);
        var items =
                reads.options(project, enrollment.databaseTime()).stream()
                        .map(row -> RegistrationResponseAssembler.option(row, profile, receipt))
                        .toList();
        return new RegistrationResponses.Options(items);
    }

    public PageResponse<RegistrationResponses.CandidateItem> list(int page, int size) {
        long candidate = access.current().id();
        PageQuery query = new PageQuery(page, size, "");
        var items =
                reads.ownList(candidate, query.size(), query.offset()).stream()
                        .map(RegistrationResponseAssembler::candidate)
                        .toList();
        long total =
                registrations.selectCount(
                        Wrappers.<Registration>lambdaQuery()
                                .eq(Registration::getCandidateId, candidate));
        return query.response(items, total);
    }

    public RegistrationResponses.CandidateItem detail(long id) {
        var row = reads.ownDetail(access.current().id(), id);
        if (row == null) throw ApiException.notFound();
        return RegistrationResponseAssembler.candidate(row);
    }
}
