package com.examsystem.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examsystem.catalog.entity.School;
import org.apache.ibatis.annotations.Mapper;

/** 学校单表分页和查询复用 Plus；写事务读取学校时使用固定 FOR SHARE 锁定其有效状态。 */
@Mapper
public interface SchoolMapper extends BaseMapper<School> {}
