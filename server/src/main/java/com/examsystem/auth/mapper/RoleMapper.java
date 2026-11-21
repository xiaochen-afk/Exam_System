package com.examsystem.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examsystem.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/** 角色单表读取及分页复用 MyBatis-Plus；允许动作仍由角色/范围的权限字典提供。 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {}
