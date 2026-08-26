/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

/**
  * 调查生命周期状态。
  * @author Codex
  * @since 2026-08-25
  */
public enum InvestigationStatus {
    ACCEPTED,
    RUNNING,
    PAUSED,
    COMPLETED,
    INCONCLUSIVE,
    FAILED,
    CANCELLED;

    /**
     * 判断状态是否为不可逆终态。
     *
     * @return 终态返回 true
     */
    public boolean terminal() {
        return this == COMPLETED || this == INCONCLUSIVE || this == FAILED || this == CANCELLED;
    }

    /**
     * 判断终态是否有资格产生评价事件。
     *
     * @return 可评价返回 true
     */
    public boolean evaluationEligible() {
        return this == COMPLETED || this == INCONCLUSIVE;
    }
}
