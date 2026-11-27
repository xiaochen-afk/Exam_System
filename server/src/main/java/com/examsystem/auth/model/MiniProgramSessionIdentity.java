package com.examsystem.auth.model;

/** 当前请求已通过认证的小程序会话与考生身份，仅含系统内部 ID。 */
public record MiniProgramSessionIdentity(long sessionId, long candidateId, long sessionVersion) {}
