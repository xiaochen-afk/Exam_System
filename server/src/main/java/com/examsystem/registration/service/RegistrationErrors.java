package com.examsystem.registration.service;

import com.examsystem.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** 报名业务冲突的稳定错误；文案不携带人数、个人资料或底层数据库异常。 */
public final class RegistrationErrors {
    private RegistrationErrors() {}

    public static ApiException profileRequired() {
        return conflict("PROFILE_REQUIRED", "请先完善有效的考生类型及学校资料");
    }

    public static ApiException alreadyRegistered() {
        return conflict("ALREADY_REGISTERED", "该报考项目已有报名记录，请查看我的报名");
    }

    public static ApiException unavailable() {
        return conflict("REGISTRATION_UNAVAILABLE", "该场次当前不可报名，请刷新可报考点");
    }

    public static ApiException full() {
        return conflict("CAPACITY_FULL", "该场次名额已满，请选择其他可报考点");
    }

    public static ApiException busy() {
        return conflict("REGISTRATION_BUSY", "报名请求较多，请稍后重试或查看我的报名");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
