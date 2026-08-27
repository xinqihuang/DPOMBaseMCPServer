/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.mcp.config.McpServerConfig;
import com.huawei.smartom.agentic.monitoring.obs.ObsEvidenceService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * OBS 证据转移有效 MCP 暴露面测试：仅开 OBS gate 时，只暴露 OBS put/head/get。
 *
 * @author h00884391
 * @since 2026-08-15
 */
class ObsMcpExposureTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObsEvidenceService.class, () -> mock(ObsEvidenceService.class))
            .withUserConfiguration(ObsEvidenceTool.class, McpServerConfig.class);

    @Test
    @DisplayName("仅开 OBS gate 时只暴露 OBS put/head/get")
    void obsGateExposesOnlyObsTransferTools() {
        runner.withPropertyValues("dpom.obs.transfer-tools-enabled=true")
                .run(context -> {
                    ToolCallbackProvider provider = context.getBean(ToolCallbackProvider.class);
                    Set<String> names = new HashSet<>();
                    for (ToolCallback callback : provider.getToolCallbacks()) {
                        names.add(callback.getToolDefinition().name());
                    }
                    assertThat(names).containsExactlyInAnyOrder(
                            "put_evidence_package", "head_evidence_package", "get_evidence_package");
                });
    }
}
