package com.examsystem.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.auth.entity.WebCredential;
import com.examsystem.auth.model.AuthData;
import org.apache.ibatis.annotations.Mapper;

/** 基于 MyBatis-Plus 的凭据 CRUD；认证查询显式选择哈希，其余默认查询不载入密码字段。 */
@Mapper
public interface WebCredentialMapper extends BaseMapper<WebCredential> {
    /** identifier 须已规范化；生成列只用于匹配，不接受外部写入。 */
    default AuthData.Credential findForAuthentication(String identifier) {
        WebCredential row =
                selectOne(
                        Wrappers.<WebCredential>lambdaQuery()
                                .select(WebCredential::getUserId, WebCredential::getPasswordHash)
                                .eq(WebCredential::getIdentifierNormalized, identifier));
        return row == null ? null : new AuthData.Credential(row.getUserId(), row.getPasswordHash());
    }

    /** 由账号事务调用，hash 必须已编码；唯一标识冲突由调用方转成业务错误并回滚。 */
    default void create(long userId, String identifier, String hash) {
        WebCredential row = new WebCredential();
        row.setUserId(userId);
        row.setIdentifier(identifier);
        row.setPasswordHash(hash);
        insert(row);
    }
}
