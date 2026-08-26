/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

import java.time.Instant;

/**
  * 部署管理的调查源权威 epoch。
  *
  * @param service service
  * @param epoch epoch
  * @param activeFrom activeFrom
  * @author Codex
  * @since 2026-08-25
  */
public record AuthorityEpoch(String service, String epoch, Instant activeFrom) {
    public AuthorityEpoch {
        if (!"DPOMBaseMCPServer".equals(service)) {
            throw new IllegalArgumentException("service");
        }
        epoch = DomainRules.id(epoch, "epoch");
        activeFrom = DomainRules.required(activeFrom, "activeFrom");
    }
}
