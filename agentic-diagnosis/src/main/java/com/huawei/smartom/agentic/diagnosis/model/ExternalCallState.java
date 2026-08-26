/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

/**
  * 外部调用在崩溃与超时下的可恢复状态。
  * @author Codex
  * @since 2026-08-25
  */
public enum ExternalCallState {
    PLANNED,
    IN_FLIGHT,
    SUCCEEDED,
    FAILED,
    UNCERTAIN;
}
