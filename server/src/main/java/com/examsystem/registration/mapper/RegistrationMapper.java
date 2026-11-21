package com.examsystem.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examsystem.registration.entity.Registration;
import org.apache.ibatis.annotations.Mapper;

/** 报名常规插入、本人项目判重和计数复用 Plus；必须在报名事务取得相应锁后写入。 */
@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {}
