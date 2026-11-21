package com.examsystem.registration.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.common.error.ApiException;
import com.examsystem.registration.assembler.RegistrationResponseAssembler;
import com.examsystem.registration.dto.RegistrationResponses;
import com.examsystem.registration.entity.Registration;
import com.examsystem.registration.mapper.EnrollmentMapper;
import com.examsystem.registration.mapper.RegistrationMapper;
import com.examsystem.registration.mapper.RegistrationReadMapper;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 报名命令入口；每次重试是独立完整事务，名额、报名与资料快照整体提交或回滚。 */
@Service
public class RegistrationCommandService {
    private static final Logger LOG = LoggerFactory.getLogger(RegistrationCommandService.class);
    private static final int MAX_ATTEMPTS = 3;
    private final CandidateAccess access;
    private final CandidateProfileService profiles;
    private final EnrollmentMapper enrollment;
    private final RegistrationMapper registrations;
    private final RegistrationReadMapper reads;
    private final TransactionTemplate transaction;

    public RegistrationCommandService(
            CandidateAccess access,
            CandidateProfileService profiles,
            EnrollmentMapper enrollment,
            RegistrationMapper registrations,
            RegistrationReadMapper reads,
            PlatformTransactionManager transactions) {
        this.access = access;
        this.profiles = profiles;
        this.enrollment = enrollment;
        this.registrations = registrations;
        this.reads = reads;
        this.transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(10);
    }

    // region 命令入口与有限事务重试

    public RegistrationResponses.CandidateItem submit(long sessionId, String requestId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transaction.execute(status -> registerOnce(sessionId, requestId));
            } catch (PessimisticLockingFailureException | QueryTimeoutException locked) {
                LOG.warn(
                        "event=registration_transaction_retry attempt={} requestId={}",
                        attempt,
                        requestId);
                if (attempt == MAX_ATTEMPTS) throw RegistrationErrors.busy();
            }
        }
        throw RegistrationErrors.busy();
    }

    // endregion

    // region 单次事务：锁定、复核、占额、保存与回执

    private RegistrationResponses.CandidateItem registerOnce(long sessionId, String requestId) {
        var candidate = access.lockCurrent();
        var profile = profiles.lockForRegistration(candidate.id());
        var session = lockSessionResources(sessionId);
        // 账号行锁也串行化同一考生的跨考点提交；唯一约束仍是项目防重复的最终防线。
        long existing =
                registrations.selectCount(
                        Wrappers.<Registration>lambdaQuery()
                                .eq(Registration::getCandidateId, candidate.id())
                                .eq(Registration::getProjectId, session.projectId()));
        if (existing != 0) throw RegistrationErrors.alreadyRegistered();
        LocalDateTime now = enrollment.databaseTime();
        requireOpen(session, now);
        if (enrollment.reserve(session.id(), now) != 1) throw RegistrationErrors.unavailable();

        Registration row = new Registration();
        row.setRegistrationNumber(
                "R" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        row.setCandidateId(candidate.id());
        row.setProjectId(session.projectId());
        row.setSessionId(session.id());
        row.setSiteId(session.siteId());
        row.setCandidateNameSnapshot(candidate.displayName());
        row.setCandidateTypeSnapshot(profile.candidateType());
        row.setSchoolIdSnapshot(profile.schoolId());
        row.setSchoolNameSnapshot(profile.schoolName());
        row.setStatus("REGISTERED");
        row.setSubmittedAt(now);
        try {
            registrations.insert(row);
        } catch (DuplicateKeyException conflict) {
            // 仅识别固定约束名，不把异常原文（可能包含姓名等参数）返回或写入日志。
            String detail = conflict.getMostSpecificCause().getMessage();
            if (detail != null && detail.contains("uq_registration_candidate_project")) {
                throw RegistrationErrors.alreadyRegistered();
            }
            throw RegistrationErrors.conflict("STATE_CONFLICT", "报名记录发生冲突，请刷新后重试");
        }
        var receipt = reads.ownDetail(candidate.id(), row.getId());
        if (receipt == null) throw new IllegalStateException("Registration receipt unavailable");
        logAfterCommit(row, requestId);
        return RegistrationResponseAssembler.candidate(receipt);
    }

    /** 当前读重新核对预读关联，未来项目/场次维护必须沿用此父资源到场次的锁序。 */
    private EnrollmentMapper.Session lockSessionResources(long sessionId) {
        var binding = enrollment.binding(sessionId);
        if (binding == null) throw ApiException.notFound();
        String subjectStatus = enrollment.lockSubject(binding.subjectId());
        var project = enrollment.lockProject(binding.projectId());
        var site = enrollment.lockSite(binding.siteId());
        Integer open = enrollment.lockProjectSite(binding.projectId(), binding.siteId());
        var session = enrollment.lockSession(sessionId);
        if (session == null || "DRAFT".equals(session.status())) throw ApiException.notFound();
        if (project == null
                || project.subjectId() != binding.subjectId()
                || session.projectId() != binding.projectId()
                || session.siteId() != binding.siteId()
                || !"ACTIVE".equals(subjectStatus)
                || site == null
                || !"ACTIVE".equals(site.status())
                || !"QUALIFIED".equals(site.qualificationStatus())
                || open == null
                || open != 1) {
            throw RegistrationErrors.unavailable();
        }
        return session;
    }

    private static void requireOpen(EnrollmentMapper.Session session, LocalDateTime now) {
        if (!"PUBLISHED".equals(session.status())
                || now.isBefore(session.registrationStartsAt())
                || !now.isBefore(session.registrationEndsAt())
                || session.ticketPublishedAt() != null
                || session.scorePublishedAt() != null) {
            throw RegistrationErrors.unavailable();
        }
        if (session.registeredCount() >= session.capacity()) throw RegistrationErrors.full();
    }

    // endregion

    private static void logAfterCommit(Registration row, String requestId) {
        long registrationId = row.getId();
        long candidateId = row.getCandidateId();
        long sessionId = row.getSessionId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        LOG.info(
                                "event=candidate_registration_created registrationId={} candidateId={} sessionId={} requestId={}",
                                registrationId,
                                candidateId,
                                sessionId,
                                requestId);
                    }
                });
    }
}
