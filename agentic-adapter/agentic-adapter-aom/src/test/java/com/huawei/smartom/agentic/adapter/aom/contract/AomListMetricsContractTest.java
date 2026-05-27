/*
 * Copyright (c) Huawei Technologies Co., Ltd 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.contract;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.huaweicloud.sdk.aom.v2.model.Dimension;
import com.huaweicloud.sdk.aom.v2.model.ListMetricItemsResponse;
import com.huaweicloud.sdk.aom.v2.model.MetaDataSeries;
import com.huaweicloud.sdk.aom.v2.model.MetricItemResultAPI;
import com.huaweicloud.sdk.aom.v2.model.QueryMetricItemOptionParam;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Type-contract tests for Huawei Cloud AOM SDK v3.1.177.
 *
 * <p>Purpose: detect breaking changes in SDK class shape (renamed / removed fields)
 * that the Java compiler would not catch (e.g. when accessed via reflection or builder).
 *
 * <p>If these tests fail after an SDK upgrade, the adapter mapping logic in
 * {@code AomMetricsAdapterImpl} must be reviewed before releasing.
 *
 * @author h00884391
 * @since 2026-05-21
 */
class AomListMetricsContractTest {

    @Test
    @DisplayName("TC-01: MetricItemResultAPI has expected fields")
    void tc01MetricItemResultApiHasRequiredFields() {
        assertHasField(MetricItemResultAPI.class, "namespace");
        assertHasField(MetricItemResultAPI.class, "metricName");
        assertHasField(MetricItemResultAPI.class, "unit");
        assertHasField(MetricItemResultAPI.class, "dimensions");
        assertHasField(MetricItemResultAPI.class, "dimensionvaluehash");
    }

    @Test
    @DisplayName("TC-02: aom.v2.model.Dimension has name and value fields")
    void tc02DimensionHasRequiredFields() {
        assertHasField(Dimension.class, "name");
        assertHasField(Dimension.class, "value");
    }

    @Test
    @DisplayName("TC-03: MetaDataSeries has count, offset, total, nextToken fields")
    void tc03MetaDataSeriesHasRequiredFields() {
        assertHasField(MetaDataSeries.class, "count");
        assertHasField(MetaDataSeries.class, "offset");
        assertHasField(MetaDataSeries.class, "total");
        assertHasField(MetaDataSeries.class, "nextToken");
    }

    @Test
    @DisplayName("TC-04: QueryMetricItemOptionParam has NamespaceEnum with required constants")
    void tc04NamespaceEnumHasRequiredConstants() throws Exception {
        Class<?> outerClass = QueryMetricItemOptionParam.class;
        Class<?> enumClass = Arrays.stream(outerClass.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("NamespaceEnum"))
                .findFirst()
                .orElse(null);
        assertThat(enumClass).as("NamespaceEnum nested class must exist in QueryMetricItemOptionParam")
                .isNotNull();

        assertHasStaticField(enumClass, "PAAS_CONTAINER");
        assertHasStaticField(enumClass, "PAAS_NODE");
        assertHasStaticField(enumClass, "PAAS_SLA");
        assertHasStaticField(enumClass, "PAAS_AGGR");
        assertHasStaticField(enumClass, "CUSTOMMETRICS");
    }

    @Test
    @DisplayName("TC-05: sample JSON deserialises into ListMetricItemsResponse with expected field values")
    void tc05SampleJsonDeserializes() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream in = getClass().getResourceAsStream(
                "/sdk-samples/aom/list-metric-items-response.json")) {
            assertThat(in).as("AOM sample JSON resource must exist").isNotNull();
            ListMetricItemsResponse resp = mapper.readValue(in, ListMetricItemsResponse.class);

            assertThat(resp.getMetrics()).isNotEmpty();
            MetricItemResultAPI first = resp.getMetrics().get(0);
            assertThat(first.getNamespace()).isEqualTo("PAAS.CONTAINER");
            assertThat(first.getMetricName()).isEqualTo("aom_process_cpu_usage");
            assertThat(first.getUnit()).isEqualTo("Percent");
            assertThat(first.getDimensions()).hasSize(1);
            assertThat(first.getDimensions().get(0).getName()).isEqualTo("appName");
            assertThat(first.getDimensionvaluehash()).isEqualTo("a1b2c3d4e5f6");

            assertThat(resp.getMetaData()).isNotNull();
            assertThat(resp.getMetaData().getCount()).isEqualTo(1);
            assertThat(resp.getMetaData().getTotal()).isEqualTo(523);
            assertThat(resp.getMetaData().getNextToken()).isEqualTo(100);
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

    private void assertHasStaticField(Class<?> clazz, String fieldName) {
        boolean found = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .anyMatch(name -> name.equals(fieldName));
        assertThat(found)
                .as("Static field '%s' should exist in %s", fieldName, clazz.getSimpleName())
                .isTrue();
    }
}
