package com.examsystem.exam.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 复合主键关系使用专用 SQL；先锁项目、考点，再锁关系，开关仅由项目发布权限维护。 */
@Mapper
public interface ProjectSiteMapper {
    void ensure(@Param("project") long project, @Param("site") long site);

    Boolean lockOpen(@Param("project") long project, @Param("site") long site);

    void setOpen(
            @Param("project") long project, @Param("site") long site, @Param("open") boolean open);
}
