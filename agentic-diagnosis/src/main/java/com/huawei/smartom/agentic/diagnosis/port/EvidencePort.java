/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import java.util.List;

/**
  * 调查领域访问只读证据的内向端口。
  * @author Codex
  * @since 2026-08-25
  */
public interface EvidencePort {
    /**
     * 获取有界、脱敏且 provider-neutral 的证据引用。
     *
     * @param request 证据请求
     * @return 不超过请求上限的证据
     */
    List<EvidenceRecord> collect(EvidenceRequest request);
}
