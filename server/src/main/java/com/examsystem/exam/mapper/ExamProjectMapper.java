package com.examsystem.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examsystem.exam.entity.ExamProject;
import org.apache.ibatis.annotations.Mapper;

/** 常规持久化复用 Plus；调用方先判权、加锁并校验业务约束。 */
@Mapper
public interface ExamProjectMapper extends BaseMapper<ExamProject> {}
