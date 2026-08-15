/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.approval;

/**
 * 证据上传审批记录（持久化）。绑定精确身份 + 内容 checksum + 审批人 + 原因 + 过期时间。
 *
 * @param serviceCode       服务编码
 * @param investigationId   调查编号
 * @param packageId         证据包编号
 * @param sha256            证据包 SHA-256（小写十六进制）
 * @param approverRef       审批人标识
 * @param reason            审批原因
 * @param expiresAtMillis   过期时间（epoch 毫秒）
 * @param createdAtMillis   创建时间（epoch 毫秒）
 *
 * @author h00884391
 * @since 2026-08-16
 */
public record ApprovalRecord(String serviceCode, String investigationId, String packageId, String sha256,
        String approverRef, String reason, long expiresAtMillis, long createdAtMillis) {
}
