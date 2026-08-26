/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.service;

import com.huawei.smartom.agentic.diagnosis.model.Investigation;

/**
  * 终态提交结果。
  *
  * @param investigation investigation
  * @param publicationRequested publicationRequested
  * @author Codex
  * @since 2026-08-25
  */
public record TerminalizationResult(Investigation investigation, boolean publicationRequested) {}
