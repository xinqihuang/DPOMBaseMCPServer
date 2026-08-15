/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs;

import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectContent;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectMetadata;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 禁用态 OBS 证据转移适配器：默认装配，fail-closed，不连接 OBS。
 *
 * @author h00884391
 * @since 2026-08-15
 */
@Component
@ConditionalOnProperty(name = "dpom.obs.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledObsEvidenceAdapter implements ObsEvidenceAdapter {

    private static final String MESSAGE = "OBS evidence transfer is not enabled";

    @Override
    public ObsPutEvidenceResponse putEvidence(ObsPutEvidenceRequest request) {
        throw new SmartomException(ErrorCode.OBS_UNAVAILABLE, MESSAGE);
    }

    @Override
    public ObsObjectMetadata headEvidence(String objectKey) {
        throw new SmartomException(ErrorCode.OBS_UNAVAILABLE, MESSAGE);
    }

    @Override
    public ObsObjectContent getEvidence(String objectKey) {
        throw new SmartomException(ErrorCode.OBS_UNAVAILABLE, MESSAGE);
    }
}
