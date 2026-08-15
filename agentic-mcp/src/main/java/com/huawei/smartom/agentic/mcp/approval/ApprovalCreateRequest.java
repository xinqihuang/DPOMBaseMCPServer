/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.approval;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 审批创建请求（字段白名单，未知字段由 Jackson FAIL_ON_UNKNOWN_PROPERTIES 拒绝）。
 *
 * @param serviceCode     服务编码
 * @param investigationId 调查编号
 * @param packageId       证据包编号
 * @param sha256          证据包 SHA-256
 * @param approverRef     审批人标识
 * @param reason          审批原因
 *
 * @author h00884391
 * @since 2026-08-16
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ApprovalCreateRequest(String serviceCode, String investigationId, String packageId, String sha256,
        String approverRef, String reason) {
}
