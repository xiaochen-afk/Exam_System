package com.examsystem.registration.model;

/** 本次读取或写事务内的资料快照；complete 包含学校有效性，不等价于学籍核验结论。 */
public record CandidateProfileState(
        String candidateType, Long schoolId, String schoolName, boolean complete) {}
