/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.obs.ObsEvidenceService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OBS 证据转移工具独立 gate 注册边界测试（不复用 write-tools-enabled / action-enabled）。
 *
 * @author h00884391
 * @since 2026-08-15
 */
class ObsWriteToolRegistrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObsEvidenceService.class, () -> mock(ObsEvidenceService.class))
            .withUserConfiguration(ObsEvidenceTool.class);

    @Test
    @DisplayName("默认运行时不注册 OBS 工具")
    void obsToolsAbsentByDefault() {
        runner.run(this::assertObsToolsAbsent);
    }

    @Test
    @DisplayName("仅启用 write-tools-enabled 不注册 OBS 工具（独立 gate）")
    void writeToolsEnabledDoesNotRegisterObsTools() {
        runner.withPropertyValues("dpom.mcp.write-tools-enabled=true", "spring.profiles.active=action-enabled")
                .run(this::assertObsToolsAbsent);
    }

    @Test
    @DisplayName("仅启用 transfer-tools-enabled 注册 OBS 工具")
    void transferToolsEnabledRegistersObsTools() {
        runner.withPropertyValues("dpom.obs.transfer-tools-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ObsEvidenceTool.class));
    }

    private void assertObsToolsAbsent(org.springframework.context.ApplicationContext context) {
        assertThat(context.getBeansOfType(ObsEvidenceTool.class)).isEmpty();
    }
}
