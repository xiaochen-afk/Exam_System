package com.examsystem.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus 公共装配；分页不能代替业务 SQL 在分页前执行的授权过滤。 */
@Configuration
public class MybatisPlusConfiguration {
    /** 启动时完成跨 XML include 的解析，不把缺失片段延迟到用户首次请求。无需连接数据库。 */
    @Bean
    SmartInitializingSingleton resolveMappedStatements(SqlSessionFactory sqlSessionFactory) {
        return () -> sqlSessionFactory.getConfiguration().getMappedStatementNames();
    }

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
