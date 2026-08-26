/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import java.time.Instant;

/**
  * 可测试的领域时钟。
  * @author Codex
  * @since 2026-08-25
  */
public interface ClockPort {
    /**
     * 返回当前领域时间。
     *
     * @return 当前时间
     */
    Instant now();
}
