/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.approval;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 审批创建响应（回显身份 + 审批人 + 过期时间，不含 reason/密钥）。
 *
 * @param serviceCode     服务编码
 * @param investigationId 调查编号
 * @param packageId       证据包编号
 * @param sha256          证据包 SHA-256
 * @param approverRef     审批人标识
 * @param expiresAtMillis 过期时间（epoch 毫秒）
 *
 * @author h00884391
 * @since 2026-08-16
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ApprovalRecordResponse(String serviceCode, String investigationId, String packageId, String sha256,
        String approverRef, long expiresAtMillis) {
}
