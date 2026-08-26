/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 生产启动前验证 deployment-managed Investigation schema。
 *
 * @author Codex
 * @since 2026-08-25
 */
public final class InvestigationSchemaReadiness implements InitializingBean {

    private final JdbcTemplate jdbc;
    private final int expectedVersion;

    /**
     * 创建 schema readiness 门禁。
     *
     * @param jdbc JDBC 模板
     * @param expectedVersion 期望版本
     */
    public InvestigationSchemaReadiness(JdbcTemplate jdbc, int expectedVersion) {
        this.jdbc = jdbc;
        this.expectedVersion = expectedVersion;
    }

    /**
     * 验证 schema 版本和兼容状态，否则拒绝启动。
     */
    @Override
    public void afterPropertiesSet() {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dpom_schema_state "
                    + "WHERE component = 'investigation' AND schema_version = ? "
                    + "AND compatibility_state = 'READY'", Integer.class, expectedVersion);
            if (count == null || count != 1) {
                throw new IllegalStateException("investigation schema is not ready");
            }
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("investigation schema readiness failed");
        }
    }
}
