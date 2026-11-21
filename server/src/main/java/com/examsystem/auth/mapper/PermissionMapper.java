package com.examsystem.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 读取判权所需的真实资源归属，不采信客户端提供的项目或考点归属。 */
@Mapper
public interface PermissionMapper {
    /** 场次的项目、考点归属快照；单次判权必须匹配同一条有效授权。 */
    record SessionScope(long id, long projectId, long siteId) {}

    /** 不存在时返回 null，由 Service 决定是否统一映射为范围外的 404。 */
    SessionScope session(@Param("id") long id);
}
