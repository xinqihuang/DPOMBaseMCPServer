/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.aom.dto.AomFillValue;
import com.huawei.smartom.agentic.adapter.aom.dto.AomLogCategory;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricPeriod;
import com.huawei.smartom.agentic.adapter.aom.dto.AomMetricStatistic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 AOM 封闭集枚举被 Spring AI 反射进工具 JSON Schema（T26 验收标准）：
 * statistics / period / fill_value / category 的合法取值在 Schema 中可见。
 *
 * @author h00884391
 * @since 2026-06-11
 */
class AomEnumSchemaTest {

    @Test
    @DisplayName("query_aom_metric_data schema exposes statistics/period/fill_value enum values")
    void metricDataSchemaExposesEnums() {
        String schema = schemaOf(AomMetricDataTool.class, "queryAomMetricData");
        for (AomMetricStatistic statistic : AomMetricStatistic.values()) {
            assertPresent(schema, statistic.getValue(), statistic.name());
        }
        for (AomMetricPeriod period : AomMetricPeriod.values()) {
            assertPresent(schema, String.valueOf(period.getSeconds()), period.name());
        }
        for (AomFillValue fill : AomFillValue.values()) {
            assertPresent(schema, fill.getValue(), fill.name());
        }
    }

    @Test
    @DisplayName("query_logs schema exposes category enum values")
    void logSchemaExposesCategoryEnum() {
        String schema = schemaOf(AomLogTool.class, "queryLogs");
        for (AomLogCategory category : AomLogCategory.values()) {
            assertPresent(schema, category.getValue(), category.name());
        }
    }

    private static void assertPresent(String schema, String literal, String constantName) {
        boolean present = schema.contains(literal) || schema.contains(constantName);
        assertThat(present)
                .as("schema should list %s (or %s), schema=%s", literal, constantName, schema)
                .isTrue();
    }

    private static String schemaOf(Class<?> toolClass, String methodName) {
        Method method = Arrays.stream(toolClass.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("tool method not found: " + methodName));
        return JsonSchemaGenerator.generateForMethodInput(method);
    }
}
