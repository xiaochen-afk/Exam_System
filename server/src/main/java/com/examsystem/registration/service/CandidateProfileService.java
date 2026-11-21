package com.examsystem.registration.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examsystem.auth.entity.CandidateProfile;
import com.examsystem.auth.mapper.CandidateProfileMapper;
import com.examsystem.catalog.entity.School;
import com.examsystem.catalog.mapper.SchoolMapper;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiException;
import com.examsystem.common.query.PageQuery;
import com.examsystem.registration.assembler.RegistrationResponseAssembler;
import com.examsystem.registration.dto.RegistrationRequests;
import com.examsystem.registration.dto.RegistrationResponses;
import com.examsystem.registration.model.CandidateProfileState;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 考生资料及学校目录；更新当前资料不回写任何既有报名快照，也不代表学籍已核验。 */
@Service
public class CandidateProfileService {
    private static final Logger LOG = LoggerFactory.getLogger(CandidateProfileService.class);
    private final CandidateAccess access;
    private final CandidateProfileMapper profiles;
    private final SchoolMapper schools;
    private final Clock clock;

    public CandidateProfileService(
            CandidateAccess access,
            CandidateProfileMapper profiles,
            SchoolMapper schools,
            Clock clock) {
        this.access = access;
        this.profiles = profiles;
        this.schools = schools;
        this.clock = clock;
    }

    // region 本人资料与学校目录读取

    @Transactional(readOnly = true)
    public RegistrationResponses.Profile current() {
        return RegistrationResponseAssembler.profile(state(access.current().id()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<RegistrationResponses.NamedOption> schools(
            int page, int size, String keyword) {
        access.current();
        PageQuery query = new PageQuery(page, size, keyword);
        var filter =
                Wrappers.<School>lambdaQuery()
                        .select(School::getId, School::getName)
                        .eq(School::getStatus, "ACTIVE");
        if (!query.keyword().isEmpty())
            filter.apply("name LIKE {0} ESCAPE '!'", "%" + query.keyword() + "%");
        filter.orderByAsc(School::getName, School::getId);
        var result = schools.selectPage(new Page<>(page, size), filter);
        return query.response(
                result.getRecords().stream()
                        .map(
                                row ->
                                        new RegistrationResponses.NamedOption(
                                                row.getId().toString(), row.getName()))
                        .toList(),
                result.getTotal());
    }

    /** 已由调用方校验考生身份；选项读取使用此快照，不在各场次循环中查学校。 */
    public CandidateProfileState state(long candidateId) {
        CandidateProfile profile = profiles.selectById(candidateId);
        if (profile == null) return new CandidateProfileState(null, null, null, false);
        School school =
                profile.getSchoolId() == null ? null : schools.selectById(profile.getSchoolId());
        return state(profile, school);
    }

    // endregion

    // region 写入与报名事务内锁定

    @Transactional
    public RegistrationResponses.Profile update(RegistrationRequests.ProfileUpdate input) {
        long candidateId = access.lockCurrent().id();
        CandidateProfile profile = lockProfile(candidateId);
        if (profile == null) {
            profiles.createForCandidate(candidateId);
        }
        if (input.candidateType() == null) throw ApiException.invalid("请选择考生类型");
        School school = input.schoolId() == null ? null : lockSchool(input.schoolId());
        boolean student = input.candidateType() == RegistrationRequests.CandidateType.STUDENT;
        if (student && (school == null || !"ACTIVE".equals(school.getStatus()))) {
            throw ApiException.invalid("在校考生须选择启用的学校");
        }
        if (!student && input.schoolId() != null) throw ApiException.invalid("社会考生不能提交在读学校");
        // 显式 set(null) 清除学校，避免 Plus 的默认非空更新策略留下旧学校。
        profiles.update(
                null,
                Wrappers.<CandidateProfile>lambdaUpdate()
                        .eq(CandidateProfile::getCandidateId, candidateId)
                        .set(CandidateProfile::getCandidateType, input.candidateType().name())
                        .set(CandidateProfile::getSchoolId, input.schoolId())
                        .set(
                                CandidateProfile::getUpdatedAt,
                                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        LOG.info("event=candidate_profile_updated candidateId={}", candidateId);
                    }
                });
        return new RegistrationResponses.Profile(
                input.candidateType().name(),
                school == null ? null : school.getId().toString(),
                school == null ? null : school.getName());
    }

    /** 调用方已锁账号；锁资料及学校后生成报名快照，和资料修改使用相同顺序。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public CandidateProfileState lockForRegistration(long candidateId) {
        CandidateProfile profile = lockProfile(candidateId);
        if (profile == null) throw RegistrationErrors.profileRequired();
        School school = profile.getSchoolId() == null ? null : lockSchool(profile.getSchoolId());
        CandidateProfileState state = state(profile, school);
        if (!state.complete()) throw RegistrationErrors.profileRequired();
        return state;
    }

    private CandidateProfile lockProfile(long candidateId) {
        return profiles.selectOne(
                Wrappers.<CandidateProfile>lambdaQuery()
                        .eq(CandidateProfile::getCandidateId, candidateId)
                        .last("FOR UPDATE"));
    }

    private School lockSchool(long schoolId) {
        return schools.selectOne(
                Wrappers.<School>lambdaQuery().eq(School::getId, schoolId).last("FOR SHARE"));
    }

    // endregion

    private static CandidateProfileState state(CandidateProfile profile, School school) {
        boolean social =
                "SOCIAL".equals(profile.getCandidateType()) && profile.getSchoolId() == null;
        boolean student =
                "STUDENT".equals(profile.getCandidateType())
                        && school != null
                        && "ACTIVE".equals(school.getStatus());
        return new CandidateProfileState(
                profile.getCandidateType(),
                profile.getSchoolId(),
                school == null ? null : school.getName(),
                social || student);
    }
}
