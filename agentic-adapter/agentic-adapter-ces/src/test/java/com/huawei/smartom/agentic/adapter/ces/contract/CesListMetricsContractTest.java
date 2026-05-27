/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.huaweicloud.sdk.ces.v1.model.ListMetricsResponse;
import com.huaweicloud.sdk.ces.v1.model.MetaData;
import com.huaweicloud.sdk.ces.v1.model.MetricInfoList;
import com.huaweicloud.sdk.ces.v1.model.MetricsDimension;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Type-contract tests for Huawei Cloud CES SDK v3.1.196.
 *
 * <p>Purpose: detect breaking changes in SDK class shape (renamed / removed fields)
 * that the Java compiler would not catch (e.g. when accessed via reflection or builder).
 *
 * <p>If these tests fail after an SDK upgrade, the adapter mapping logic in
 * {@code CesMetricsAdapterImpl} must be reviewed.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class CesListMetricsContractTest {

    @Test
    @DisplayName("TC-01: SDK MetricInfoList has expected fields")
    void metricInfoListHasRequiredFields() {
        assertHasField(MetricInfoList.class, "namespace");
        assertHasField(MetricInfoList.class, "metricName");
        assertHasField(MetricInfoList.class, "unit");
        assertHasField(MetricInfoList.class, "dimensions");
    }

    @Test
    @DisplayName("TC-02: SDK MetricsDimension has expected fields")
    void metricsDimensionHasRequiredFields() {
        assertHasField(MetricsDimension.class, "name");
        assertHasField(MetricsDimension.class, "value");
    }

    @Test
    @DisplayName("TC-03: SDK MetaData has expected fields")
    void metaDataHasRequiredFields() {
        assertHasField(MetaData.class, "count");
        assertHasField(MetaData.class, "marker");
        assertHasField(MetaData.class, "total");
    }

    @Test
    @DisplayName("TC-04: sample JSON deserialises into SDK ListMetricsResponse without nulls in critical fields")
    void sampleJsonDeserializes() throws IOException {
        ObjectMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();

        try (InputStream in = getClass().getResourceAsStream("/sdk-samples/ces/list-metrics-response.json")) {
            assertThat(in).as("sample JSON resource must exist").isNotNull();
            ListMetricsResponse resp = mapper.readValue(in, ListMetricsResponse.class);

            assertThat(resp.getMetrics()).isNotEmpty();
            MetricInfoList first = resp.getMetrics().get(0);
            assertThat(first.getNamespace()).isEqualTo("SYS.ECS");
            assertThat(first.getMetricName()).isEqualTo("cpu_util");
            assertThat(first.getUnit()).isEqualTo("%");
            assertThat(first.getDimensions()).hasSize(1);
            assertThat(first.getDimensions().get(0).getName()).isEqualTo("instance_id");

            assertThat(resp.getMetaData()).isNotNull();
            assertThat(resp.getMetaData().getCount()).isEqualTo(1);
            assertThat(resp.getMetaData().getTotal()).isEqualTo(7);
            assertThat(resp.getMetaData().getMarker()).isNotBlank();
        }
    }

    private void assertHasField(Class<?> clazz, String fieldName) {
        boolean found = Arrays.stream(clazz.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(name -> name.equals(fieldName));
        assertThat(found)
                .as("Field '%s' should exist in %s", fieldName, clazz.getSimpleName())
                .isTrue();
    }
}
