package com.examsystem.registration.controller;

import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiErrors;
import com.examsystem.registration.dto.RegistrationRequests;
import com.examsystem.registration.dto.RegistrationResponses;
import com.examsystem.registration.service.CandidateProfileService;
import com.examsystem.registration.service.CandidateRegistrationService;
import com.examsystem.registration.service.RegistrationCommandService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 考生报名 HTTP 入口；沿用会话与 CSRF，身份/项目/考点和快照均由服务端决定。 */
@RestController
@RequestMapping("/api/v1")
public class CandidateRegistrationController {
    private final CandidateProfileService profiles;
    private final CandidateRegistrationService reads;
    private final RegistrationCommandService commands;

    public CandidateRegistrationController(
            CandidateProfileService profiles,
            CandidateRegistrationService reads,
            RegistrationCommandService commands) {
        this.profiles = profiles;
        this.reads = reads;
        this.commands = commands;
    }

    // region 报考资料与学校目录

    @GetMapping("/me/candidate-profile")
    RegistrationResponses.Profile profile() {
        return profiles.current();
    }

    @PutMapping("/me/candidate-profile")
    RegistrationResponses.Profile updateProfile(
            @Valid @RequestBody RegistrationRequests.ProfileUpdate input) {
        return profiles.update(input);
    }

    @GetMapping("/schools")
    PageResponse<RegistrationResponses.NamedOption> schools(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return profiles.schools(page, size, keyword);
    }

    // endregion

    // region 选点与原子报名

    @GetMapping("/exam-sessions/{id}/registration-options")
    RegistrationResponses.Options options(@PathVariable @Positive long id) {
        return reads.options(id);
    }

    /** 201 Location 指向可按本人身份重新获取的回执；重复报名使用明确冲突错误。 */
    @PostMapping("/exam-sessions/{id}/registrations")
    ResponseEntity<RegistrationResponses.CandidateItem> register(
            @PathVariable @Positive long id,
            @RequestBody RegistrationRequests.Submission input,
            HttpServletRequest request) {
        var receipt = commands.submit(id, ApiErrors.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/me/registrations/" + receipt.id()))
                .body(receipt);
    }

    // endregion

    // region 本人记录与回执

    @GetMapping("/me/registrations")
    PageResponse<RegistrationResponses.CandidateItem> registrations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reads.list(page, size);
    }

    @GetMapping("/me/registrations/{id}")
    RegistrationResponses.CandidateItem receipt(@PathVariable @Positive long id) {
        return reads.detail(id);
    }

    // endregion
}
