/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.authority;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

/**
 * 权威切换证据与分裂所有权门禁测试。
 *
 * @author Codex
 * @since 2026-08-25
 */
class AuthorityActivationPropertiesTest {
    @Test
    void failsClosedWhenParityOrOldAuthorityDrainIsMissing() {
        var incomplete = properties(false, true);
        assertThatThrownBy(incomplete::validateActivation)
                .isInstanceOf(IllegalStateException.class).hasMessage("INCOMPLETE_PARITY_EVIDENCE");
        var split = properties(true, false);
        assertThatThrownBy(split::validateActivation)
                .isInstanceOf(IllegalStateException.class).hasMessage("SPLIT_AUTHORITY_RISK");
    }

    @Test
    void admitsOnlyACompleteRecordedCutover() {
        assertThatCode(() -> properties(true, true).validateActivation()).doesNotThrowAnyException();
    }

    private AuthorityActivationProperties properties(boolean parity, boolean stopped) {
        return new AuthorityActivationProperties(true, "epoch-2", Instant.parse("2026-08-25T00:00:00Z"),
                "dpom-base-prod-1", List.of("diagnosis-event/2.0", "diagnosis-progress/1.0"),
                true, true, parity, true, true, stopped, true);
    }
}
