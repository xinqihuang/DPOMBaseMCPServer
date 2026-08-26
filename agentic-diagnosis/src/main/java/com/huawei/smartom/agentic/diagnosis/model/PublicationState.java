/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

/**
 * Durable publication lifecycle.
 *
 * @author Codex
 * @since 2026-08-25
 */
public enum PublicationState {
    PENDING,
    LEASED,
    ACKNOWLEDGED,
    TERMINAL_FAILURE
}
