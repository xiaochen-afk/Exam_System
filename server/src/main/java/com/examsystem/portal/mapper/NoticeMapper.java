package com.examsystem.portal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.portal.entity.Notice;
import org.apache.ibatis.annotations.Mapper;

/** 资讯单表过滤与分页复用 MyBatis-Plus；公开可见条件统一由 NoticeService 维护。 */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {
    default LambdaQueryWrapper<Notice> withContent() {
        return Wrappers.<Notice>lambdaQuery()
                .select(
                        Notice::getId,
                        Notice::getCategory,
                        Notice::getTitle,
                        Notice::getSummary,
                        Notice::getBody,
                        Notice::getPublisherLabel,
                        Notice::getPinned,
                        Notice::getFeatured,
                        Notice::getStatus,
                        Notice::getPublishedAt,
                        Notice::getCreatedAt,
                        Notice::getUpdatedAt,
                        Notice::getWithdrawnAt);
    }
}
