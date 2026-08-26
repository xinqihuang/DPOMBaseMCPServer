/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 使用常量时间比较验证 Portal 独立只读令牌。
 *
 * @author Codex
 * @since 2026-08-25
 */
public final class PortalAuthorization {
    private final byte[] expected;

    /**
     * 创建授权校验器。
     *
     * @param properties 查询 API 配置
     */
    public PortalAuthorization(ProgressApiProperties properties) {
        properties.validate();
        expected = properties.bearerToken().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 验证 Bearer 请求头。
     *
     * @param authorization Authorization 请求头
     * @return 匹配返回 true
     */
    public boolean permits(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        byte[] supplied = authorization.substring(7).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }
}
