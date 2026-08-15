/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.obs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 跨仓契约测试：读取由 DPOMAgent 真实 {@code PackageSerializer} 生成的固定 fixture（非手工拼 ZIP），
 * 断言校验器接受真实包，并逐字段核对 camelCase manifest 契约（schemaVersion/packageId/service/environment/
 * release/commit/timeRange/entries），以检测 DPOMAgent 序列化契约漂移。
 *
 * <p>fixture 由 scripts/regenerate-obs-contract-fixture.ps1 生成；本测试按结构（而非字节）校验，不依赖 ZIP
 * 时间戳等非契约元数据。
 *
 * @author h00884391
 * @since 2026-08-15
 */
class PackageSerializerContractTest {

    private static final String FIXTURE_RESOURCE = "/obs-fixtures/dpomagent-package.zip";
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String PACKAGE_ID = "pkg-contract-0001";
    private static final String SERVICE = "asset-service";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("接受 DPOMAgent PackageSerializer 真实产物并核对 camelCase manifest 契约")
    void acceptsRealSerializerOutput() throws IOException {
        byte[] content = readFixture();

        ObsProperties properties = new ObsProperties();
        properties.setMaxBytes(1048576);
        properties.setMaxEntries(200);
        DiagnosticEvidencePackageValidator validator = new DiagnosticEvidencePackageValidator(properties);

        assertThatCode(() -> validator.validate(content, SERVICE, PACKAGE_ID)).doesNotThrowAnyException();
        assertManifestContract(readManifest(content));
    }

    private void assertManifestContract(JsonNode manifest) {
        assertThat(manifest.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(manifest.get("packageId").asText()).isEqualTo(PACKAGE_ID);
        assertThat(manifest.get("service").asText()).isEqualTo(SERVICE);
        assertThat(manifest.get("environment").asText()).isEqualTo("prod");
        assertThat(manifest.get("release").asText()).isEqualTo("1.0.0");
        assertThat(manifest.get("commit").asText()).isEqualTo("abc123def456");
        assertThat(manifest.get("timeRange").asText()).isEqualTo("1h");
        JsonNode entries = manifest.get("entries");
        assertThat(entries.isArray()).isTrue();
        assertThat(entries.size()).isGreaterThanOrEqualTo(1);
        for (JsonNode entry : entries) {
            assertThat(entry.get("path").asText()).isNotBlank();
            assertThat(entry.get("checksum").asText()).hasSize(64);
            assertThat(entry.get("size").isIntegralNumber()).isTrue();
            assertThat(entry.get("size").asLong()).isGreaterThanOrEqualTo(0);
            assertThat(entry.get("category").asText()).isNotBlank();
        }
    }

    private JsonNode readManifest(byte[] content) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (MANIFEST_ENTRY.equals(entry.getName())) {
                    return mapper.readTree(zip.readAllBytes());
                }
            }
        }
        throw new IllegalStateException("manifest.json not found in contract fixture");
    }

    private byte[] readFixture() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(FIXTURE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing contract fixture: " + FIXTURE_RESOURCE);
            }
            return input.readAllBytes();
        }
    }
}
