/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

/**
  * Portal 和 Kafka 共用的受限进度状态。
  * @author Codex
  * @since 2026-08-25
  */
public enum ProgressStatus {
    ACCEPTED,
    RUNNING,
    CHECKPOINTED,
    COMPLETED,
    INCONCLUSIVE,
    FAILED,
    CANCELLED;
}
