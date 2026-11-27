package com.examsystem.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.auth.entity.WechatIdentity;
import org.apache.ibatis.annotations.Mapper;

/** 按两组唯一键查找绑定，创建通过 BaseMapper 执行。 */
@Mapper
public interface WechatIdentityMapper extends BaseMapper<WechatIdentity> {
    default WechatIdentity findByOpenId(String appId, byte[] openId) {
        return selectOne(
                Wrappers.<WechatIdentity>lambdaQuery()
                        .select(WechatIdentity::getId, WechatIdentity::getCandidateId)
                        .eq(WechatIdentity::getAppId, appId)
                        .eq(WechatIdentity::getOpenId, openId));
    }

    default boolean candidateHasBinding(long candidateId, String appId) {
        return exists(
                Wrappers.<WechatIdentity>lambdaQuery()
                        .eq(WechatIdentity::getCandidateId, candidateId)
                        .eq(WechatIdentity::getAppId, appId));
    }
}
