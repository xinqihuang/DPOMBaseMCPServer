/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.approval;

/**
 * 证据上传审批存储端口：批准、撤销、原子消费、回滚与查询。
 *
 * @author h00884391
 * @since 2026-08-16
 */
public interface ApprovalStore {

    /**
     * 记录（或覆盖）一条审批。
     *
     * @param record 审批记录，不可为 null
     */
    void approve(ApprovalRecord record);

    /**
     * 撤销一条审批。
     *
     * @param serviceCode     服务编码
     * @param investigationId 调查编号
     * @param packageId       证据包编号
     * @param sha256          证据包 SHA-256
     * @return true 表示存在并已撤销
     */
    boolean revoke(String serviceCode, String investigationId, String packageId, String sha256);

    /**
     * 原子消费一条审批（移除并返回）。过期视为不存在并清理。
     *
     * @param serviceCode     服务编码
     * @param investigationId 调查编号
     * @param packageId       证据包编号
     * @param sha256          证据包 SHA-256
     * @return 被消费的审批记录；不存在或已过期时为 {@code null}
     */
    ApprovalRecord consume(String serviceCode, String investigationId, String packageId, String sha256);

    /**
     * 回滚（恢复）一条已消费的审批，仅在目标键不存在时写入。
     *
     * @param record 审批记录，不可为 null
     */
    void restore(ApprovalRecord record);

    /**
     * 判断是否存在未过期的审批。
     *
     * @param serviceCode     服务编码
     * @param investigationId 调查编号
     * @param packageId       证据包编号
     * @param sha256          证据包 SHA-256
     * @return true 表示存在未过期审批
     */
    boolean isApproved(String serviceCode, String investigationId, String packageId, String sha256);
}
