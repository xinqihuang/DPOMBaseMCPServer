/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.policy;

/**
  * 使用稳定错误码表达可审计的领域拒绝。
  * @author Codex
  * @since 2026-08-25
  */
public final class DiagnosisDomainException extends RuntimeException {
    private final DiagnosisErrorCode errorCode;

    /**
     * 创建领域拒绝。
     *
     * @param errorCode 稳定错误码
     */
    public DiagnosisDomainException(DiagnosisErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    /**
     * 返回稳定错误码。
     *
     * @return 错误码
     */
    public DiagnosisErrorCode errorCode() {
        return this.errorCode;
    }
}
