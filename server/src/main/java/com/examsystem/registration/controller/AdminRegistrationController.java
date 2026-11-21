package com.examsystem.registration.controller;

import com.examsystem.registration.dto.RegistrationRequests;
import com.examsystem.registration.dto.RegistrationResponses;
import com.examsystem.registration.service.AdminRegistrationService;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 报名管理只读入口；人员须有实际项目/场次 registrations.read，不能用学校或全局角色替代。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminRegistrationController {
    private final AdminRegistrationService service;

    public AdminRegistrationController(AdminRegistrationService service) {
        this.service = service;
    }

    @GetMapping("/registrations")
    RegistrationResponses.AdminPage registrations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @Positive Long sessionId,
            @RequestParam(required = false) @Positive Long siteId,
            @RequestParam(required = false) RegistrationRequests.CandidateType candidateType,
            @RequestParam(required = false) RegistrationRequests.Status status,
            @RequestParam(defaultValue = "") String registrationNumber) {
        return service.list(
                page,
                size,
                new RegistrationRequests.AdminFilter(
                        sessionId, siteId, candidateType, status, registrationNumber));
    }

    @GetMapping("/registrations/{id}")
    RegistrationResponses.AdminItem detail(@PathVariable @Positive long id) {
        return service.detail(id);
    }
}
