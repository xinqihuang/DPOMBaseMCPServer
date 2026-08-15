/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.approval;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 审批控制面统一错误响应（稳定错误码 + 可读消息）。
 *
 * @param errorCode 稳定错误码
 * @param message   可读错误消息
 *
 * @author h00884391
 * @since 2026-08-16
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ApprovalErrorResponse(String errorCode, String message) {
}
