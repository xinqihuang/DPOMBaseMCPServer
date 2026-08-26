/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.ces.CesNotificationMaskService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * CES 写工具 fail-closed 注册边界测试。
 *
 * @author Codex
 * @since 2026-08-15
 */
class WriteToolRegistrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(CesNotificationMaskService.class, () -> mock(CesNotificationMaskService.class))
            .withUserConfiguration(CesCreateNotificationMaskTool.class, CesDeleteNotificationMasksTool.class);

    @Test
    @DisplayName("默认运行时不注册写工具")
    void writeToolsAreAbsentByDefault() {
        runner.run(this::assertWriteToolsAbsent);
    }

    @Test
    @DisplayName("仅启用配置开关仍不注册写工具")
    void propertyAloneDoesNotRegisterWriteTools() {
        runner.withPropertyValues("dpom.mcp.write-tools-enabled=true")
                .run(this::assertWriteToolsAbsent);
    }

    @Test
    @DisplayName("仅启用 action profile 仍不注册写工具")
    void profileAloneDoesNotRegisterWriteTools() {
        runner.withPropertyValues("spring.profiles.active=action-enabled")
                .run(this::assertWriteToolsAbsent);
    }

    @Test
    @DisplayName("同时启用两个显式开关才注册写工具")
    void dualOptInRegistersWriteTools() {
        runner.withPropertyValues(
                        "spring.profiles.active=action-enabled",
                        "dpom.mcp.write-tools-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CesCreateNotificationMaskTool.class);
                    assertThat(context).hasSingleBean(CesDeleteNotificationMasksTool.class);
                });
    }

    @Test
    @DisplayName("production profile 即使双重 opt-in 也不注册写工具")
    void productionProfileAlwaysRejectsWriteTools() {
        runner.withPropertyValues(
                        "spring.profiles.active=production,action-enabled",
                        "dpom.mcp.write-tools-enabled=true")
                .run(this::assertWriteToolsAbsent);
    }

    private void assertWriteToolsAbsent(org.springframework.context.ApplicationContext context) {
        assertThat(context.getBeansOfType(CesCreateNotificationMaskTool.class)).isEmpty();
        assertThat(context.getBeansOfType(CesDeleteNotificationMasksTool.class)).isEmpty();
    }
}
