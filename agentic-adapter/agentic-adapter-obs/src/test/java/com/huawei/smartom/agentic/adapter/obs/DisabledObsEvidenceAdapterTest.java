/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs;

import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 禁用态 OBS 证据转移适配器测试：默认 fail-closed，不连接 OBS。
 *
 * @author h00884391
 * @since 2026-08-15
 */
class DisabledObsEvidenceAdapterTest {

    private final DisabledObsEvidenceAdapter adapter = new DisabledObsEvidenceAdapter();

    @Test
    @DisplayName("未启用时 put 返回 OBS_UNAVAILABLE")
    void putEvidenceFailsClosed() {
        Throwable throwable = catchThrowable(() ->
                adapter.putEvidence(new ObsPutEvidenceRequest("key", new byte[0], "sha256")));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.OBS_UNAVAILABLE);
    }

    @Test
    @DisplayName("未启用时 head 返回 OBS_UNAVAILABLE")
    void headEvidenceFailsClosed() {
        Throwable throwable = catchThrowable(() -> adapter.headEvidence("key"));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.OBS_UNAVAILABLE);
    }

    @Test
    @DisplayName("未启用时 get 返回 OBS_UNAVAILABLE")
    void getEvidenceFailsClosed() {
        Throwable throwable = catchThrowable(() -> adapter.getEvidence("key"));

        assertThat(throwable).isInstanceOf(SmartomException.class);
        assertThat(((SmartomException) throwable).getErrorCode()).isEqualTo(ErrorCode.OBS_UNAVAILABLE);
    }
}
