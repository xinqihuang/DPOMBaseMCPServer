/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesNamespace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link CesNamespace} 受控枚举被 Spring AI 反射进工具 JSON Schema（T24 验收标准）：
 * Agent 在 Schema 中能看到全部 14 个合法取值。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class CesNamespaceSchemaTest {

    @Test
    @DisplayName("list_ces_metrics input schema exposes all 14 namespace values")
    void listToolSchemaExposesNamespaceEnum() {
        assertSchemaListsAllNamespaces(toolMethod(CesMetricsTool.class, "listCesMetrics"));
    }

    @Test
    @DisplayName("query_ces_metric_data input schema exposes all 14 namespace values")
    void queryToolSchemaExposesNamespaceEnum() {
        assertSchemaListsAllNamespaces(toolMethod(CesMetricDataTool.class, "queryCesMetricData"));
    }

    @Test
    @DisplayName("batch_query_ces_metric_data item schema exposes all 14 namespace values")
    void batchToolSchemaExposesNamespaceEnum() {
        assertSchemaListsAllNamespaces(
                toolMethod(CesBatchMetricDataTool.class, "batchQueryCesMetricData"));
    }

    private static void assertSchemaListsAllNamespaces(Method method) {
        String schema = JsonSchemaGenerator.generateForMethodInput(method);
        for (CesNamespace ns : CesNamespace.values()) {
            boolean present = schema.contains(ns.getValue()) || schema.contains(ns.name());
            assertThat(present)
                    .as("schema of %s should list namespace %s, schema=%s",
                            method.getName(), ns, schema)
                    .isTrue();
        }
    }

    private static Method toolMethod(Class<?> toolClass, String name) {
        return Arrays.stream(toolClass.getMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("tool method not found: " + name));
    }
}
