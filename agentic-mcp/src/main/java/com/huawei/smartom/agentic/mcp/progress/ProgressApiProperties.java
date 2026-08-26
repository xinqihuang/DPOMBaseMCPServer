/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Portal 调查查询与 SSE 的有界配置。
 *
 * @param enabled 是否启用
 * @param bearerToken 独立只读令牌
 * @param pageLimit 最大分页条数
 * @param maxClients 最大并发 SSE 客户端数
 * @param bufferLimit 单次发送缓冲上限
 * @param connectionDuration 单连接最长时间
 * @param heartbeatInterval 心跳周期
 * @param pollInterval 持久化日志轮询周期
 * @author Codex
 * @since 2026-08-25
 */
@ConfigurationProperties("dpom.investigation.progress-api")
public record ProgressApiProperties(boolean enabled, String bearerToken, int pageLimit, int maxClients,
                                    int bufferLimit, Duration connectionDuration,
                                    Duration heartbeatInterval, Duration pollInterval) {
    /**
     * 校验启用后的安全与容量配置。
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (bearerToken == null || bearerToken.length() < 32 || pageLimit < 1 || pageLimit > 200
                || maxClients < 1 || maxClients > 1000 || bufferLimit < 1 || bufferLimit > pageLimit
                || invalid(connectionDuration) || invalid(heartbeatInterval) || invalid(pollInterval)) {
            throw new IllegalStateException("invalid progress API configuration");
        }
    }

    private boolean invalid(Duration duration) {
        return duration == null || duration.isNegative() || duration.isZero();
    }
}
