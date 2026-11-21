package com.examsystem.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examsystem.auth.entity.CandidateProfile;
import org.apache.ibatis.annotations.Mapper;

/** 考生资料常规操作复用 BaseMapper；资料约束仍由业务服务与数据库联合保证。 */
@Mapper
public interface CandidateProfileMapper extends BaseMapper<CandidateProfile> {
    /** 注册事务内建立空资料，不能脱离账号及凭据单独提交。 */
    default void createForCandidate(long candidateId) {
        CandidateProfile row = new CandidateProfile();
        row.setCandidateId(candidateId);
        insert(row);
    }
}
