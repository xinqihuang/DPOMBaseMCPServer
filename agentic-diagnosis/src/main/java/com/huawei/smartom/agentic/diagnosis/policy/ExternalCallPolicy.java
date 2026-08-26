/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.policy;

import com.huawei.smartom.agentic.diagnosis.model.ExternalCallRecord;
import com.huawei.smartom.agentic.diagnosis.model.ExternalCallState;
import java.time.Instant;

/**
  * 防止超时或重启后盲目重复外部调用。
  * @author Codex
  * @since 2026-08-25
  */
public final class ExternalCallPolicy {
    /**
     * 将已持久化计划推进为调用中。
     *
     * @param current 当前调用记录
     * @param now 更新时间
     * @return 调用中记录
     * @throws DiagnosisDomainException 当前状态不是 PLANNED
     */
    public ExternalCallRecord begin(ExternalCallRecord current, Instant now) {
        this.require(current.state() == ExternalCallState.PLANNED);
        return this.copy(current, ExternalCallState.IN_FLIGHT, current.attempt() + 1, null, now);
    }

    /**
     * 将明确成功的调用完成。
     *
     * @param current 当前调用记录
     * @param resultCode 稳定结果码
     * @param now 更新时间
     * @return 成功记录
     * @throws DiagnosisDomainException 当前状态不是 IN_FLIGHT
     */
    public ExternalCallRecord succeed(ExternalCallRecord current, String resultCode, Instant now) {
        this.require(current.state() == ExternalCallState.IN_FLIGHT);
        return this.copy(current, ExternalCallState.SUCCEEDED, current.attempt(), resultCode, now);
    }

    /**
     * 将已发出但超时的调用标记为不确定，禁止盲重试。
     *
     * @param current 当前调用记录
     * @param now 更新时间
     * @return 不确定记录
     * @throws DiagnosisDomainException 当前状态不是 IN_FLIGHT
     */
    public ExternalCallRecord timeoutAfterDispatch(ExternalCallRecord current, Instant now) {
        this.require(current.state() == ExternalCallState.IN_FLIGHT);
        return this.copy(
                current, ExternalCallState.UNCERTAIN, current.attempt(), "OUTCOME_UNKNOWN", now);
    }

    /**
     * 使用人工或幂等查询结果消除不确定性。
     *
     * @param current 当前调用记录
     * @param succeeded 是否确认成功
     * @param now 更新时间
     * @return 已协调记录
     * @throws DiagnosisDomainException 当前状态不是 UNCERTAIN
     */
    public ExternalCallRecord reconcile(ExternalCallRecord current, boolean succeeded, Instant now) {
        this.require(current.state() == ExternalCallState.UNCERTAIN);
        ExternalCallState state = succeeded ? ExternalCallState.SUCCEEDED : ExternalCallState.FAILED;
        return this.copy(
                current,
                state,
                current.attempt(),
                succeeded ? "RECONCILED_SUCCESS" : "RECONCILED_FAILED",
                now);
    }

    private ExternalCallRecord copy(
            ExternalCallRecord current,
            ExternalCallState state,
            int attempt,
            String resultCode,
            Instant now) {
        return new ExternalCallRecord(
                current.callId(),
                current.investigationId(),
                current.idempotencyKey(),
                state,
                attempt,
                resultCode,
                now);
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new DiagnosisDomainException(DiagnosisErrorCode.EXTERNAL_CALL_STATE_CONFLICT);
        }
    }
}
