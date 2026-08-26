/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 默认关闭、显式配置后才装配的 Investigation 持久化边界。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvestigationPersistenceProperties.class)
@ConditionalOnProperty(prefix = "dpom.investigation.persistence", name = "enabled", havingValue = "true")
@MapperScan(basePackageClasses = InvestigationMapper.class,
        sqlSessionFactoryRef = "investigationSqlSessionFactory")
public class InvestigationPersistenceConfiguration {

    /**
     * 创建受限连接池，不在错误消息中包含凭据或 URL。
     *
     * @param properties 持久化配置
     * @return 数据源
     * @throws IllegalStateException 必填配置缺失
     */
    @Bean(name = "investigationDataSource", destroyMethod = "close")
    public HikariDataSource investigationDataSource(InvestigationPersistenceProperties properties) {
        if (blank(properties.jdbcUrl()) || blank(properties.username()) || blank(properties.driverClassName())) {
            throw new IllegalStateException("invalid investigation persistence configuration");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("investigation-persistence");
        config.setJdbcUrl(properties.jdbcUrl());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setDriverClassName(properties.driverClassName());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(5000);
        return new HikariDataSource(config);
    }

    /**
     * 创建只加载服务本地 XML 的 SqlSessionFactory。
     *
     * @param dataSource Investigation 数据源
     * @return SqlSessionFactory
     * @throws Exception mapper 资源无效
     */
    @Bean(name = "investigationSqlSessionFactory")
    public SqlSessionFactory investigationSqlSessionFactory(
            @Qualifier("investigationDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mappers/investigation/*.xml"));
        return factory.getObject();
    }

    /**
     * 创建服务本地事务管理器。
     *
     * @param dataSource Investigation 数据源
     * @return 事务管理器
     */
    @Bean(name = "investigationTransactionManager")
    public PlatformTransactionManager investigationTransactionManager(
            @Qualifier("investigationDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 创建 schema readiness 门禁。
     *
     * @param dataSource Investigation 数据源
     * @param properties 持久化配置
     * @return readiness 门禁
     */
    @Bean
    public InvestigationSchemaReadiness investigationSchemaReadiness(
            @Qualifier("investigationDataSource") DataSource dataSource,
            InvestigationPersistenceProperties properties) {
        return new InvestigationSchemaReadiness(new JdbcTemplate(dataSource), properties.expectedSchemaVersion());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
