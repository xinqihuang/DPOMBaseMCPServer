/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 默认关闭的 Investigation 数据库配置。
 *
 * @param enabled 是否启用持久化
 * @param jdbcUrl JDBC 地址
 * @param username 数据库用户
 * @param password 数据库密码
 * @param driverClassName JDBC 驱动类
 * @param expectedSchemaVersion 期望 schema 版本
 * @author Codex
 * @since 2026-08-25
 */
@ConfigurationProperties("dpom.investigation.persistence")
public record InvestigationPersistenceProperties(boolean enabled, String jdbcUrl, String username,
                                                  String password, String driverClassName,
                                                  int expectedSchemaVersion) {
}
