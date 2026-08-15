/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.obs;

import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 证据包校验器测试：合法包通过，任意 ZIP / 缺 manifest / 缺 checksums / 重复条目 / 路径穿越（含反斜杠、盘符、UNC）/
 * 源码 / 凭据 / 未声明条目 / checksum 或 size 不一致 / redaction-report 严格 schema / 解压超限拒绝。
 *
 * @author h00884391
 * @since 2026-08-15
 */
class DiagnosticEvidencePackageValidatorTest {

    private static final String TIMELINE = "timeline.json";
    private static final String REDACTION = "security/redaction-report.json";

    private ObsProperties properties;
    private DiagnosticEvidencePackageValidator validator;

    @BeforeEach
    void setUp() {
        properties = new ObsProperties();
        properties.setMaxBytes(200000);
        properties.setMaxEntries(200);
        validator = new DiagnosticEvidencePackageValidator(properties);
    }

    @Test
    @DisplayName("合法证据包通过校验")
    void validPackagePasses() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(TIMELINE, "{\"events\":[\"a\"]}".getBytes(StandardCharsets.UTF_8));
        sections.put(REDACTION, "{\"logs\":2}".getBytes(StandardCharsets.UTF_8));

        byte[] content = packageWithSections("pkg", "svc", sections, Map.of(), Map.of());

        assertThatCode(() -> validator.validate(content, "svc", "pkg")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非 ZIP 内容拒绝")
    void notAZipRejected() {
        Throwable throwable = catchThrowable(() ->
                validator.validate("not a zip".getBytes(StandardCharsets.UTF_8), "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("缺 manifest.json 拒绝")
    void missingManifestRejected() throws IOException {
        byte[] content = buildRawZip(List.of(zipEntry(TIMELINE, "{}".getBytes(StandardCharsets.UTF_8))));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("manifest");
    }

    @Test
    @DisplayName("缺 checksums.json 拒绝")
    void missingChecksumsRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(TIMELINE, "{}".getBytes(StandardCharsets.UTF_8));
        byte[] content = buildRawZip(List.of(
                zipEntry("manifest.json", manifestBytes("pkg", "svc", sections, Map.of(), Map.of()))));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("checksums");
    }

    @Test
    @DisplayName("重复 manifest.json 拒绝")
    void duplicateManifestRejected() throws IOException {
        byte[] manifest = manifestBytes("pkg", "svc", Map.of(), Map.of(), Map.of());
        byte[] content = buildRawZip(List.of(
                zipEntry("manifest.json", manifest), zipEntry("manifest.json", manifest)));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("duplicate");
    }

    @Test
    @DisplayName("重复非 manifest 条目拒绝")
    void duplicateSectionRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(TIMELINE, "{}".getBytes(StandardCharsets.UTF_8));
        byte[] manifest = manifestBytes("pkg", "svc", sections, Map.of(), Map.of());
        byte[] checksums = checksumsBytes(sections, Map.of());
        byte[] timeline = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] content = buildRawZip(List.of(
                zipEntry("manifest.json", manifest), zipEntry("checksums.json", checksums),
                zipEntry(TIMELINE, timeline), zipEntry(TIMELINE, timeline)));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("duplicate");
    }

    @Test
    @DisplayName("路径穿越拒绝（../）")
    void pathTraversalRejected() throws IOException {
        byte[] content = buildRawZip(List.of(zipEntry("../evil.json", "{}".getBytes(StandardCharsets.UTF_8))));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("路径穿越拒绝（反斜杠 ..\\..）")
    void backslashTraversalRejected() throws IOException {
        byte[] content = buildRawZip(List.of(zipEntry("..\\..\\evil.json", "{}".getBytes(StandardCharsets.UTF_8))));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("路径穿越拒绝（Windows 盘符）")
    void windowsDriveRejected() throws IOException {
        byte[] content = buildRawZip(List.of(zipEntry("C:/evil.json", "{}".getBytes(StandardCharsets.UTF_8))));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("路径穿越拒绝（UNC）")
    void uncRejected() throws IOException {
        byte[] content = buildRawZip(List.of(zipEntry("\\\\server\\share.json", "{}".getBytes(StandardCharsets.UTF_8))));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("源码文件拒绝")
    void sourceCodeRejected() throws IOException {
        byte[] content = buildRawZip(List.of(
                zipEntry("AssetService.java", "public class AssetService {}".getBytes(StandardCharsets.UTF_8))));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("source");
    }

    @Test
    @DisplayName("凭据标记拒绝")
    void credentialsRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(TIMELINE, "{\"password\":\"secret\"}".getBytes(StandardCharsets.UTF_8));
        byte[] content = packageWithSections("pkg", "svc", sections, Map.of(), Map.of());

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("credentials");
    }

    @Test
    @DisplayName("凭据位于 64KiB 之后仍拒绝（完整扫描）")
    void credentialsAfter64KiBRejected() throws IOException {
        byte[] section = new byte[70000];
        byte[] marker = "password=secret".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(marker, 0, section, 66000, marker.length);
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(TIMELINE, section);
        byte[] content = packageWithSections("pkg", "svc", sections, Map.of(), Map.of());

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("credentials");
    }

    @Test
    @DisplayName("未声明条目拒绝")
    void undeclaredEntryRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(TIMELINE, "{}".getBytes(StandardCharsets.UTF_8));
        sections.put(REDACTION, "{\"logs\":0}".getBytes(StandardCharsets.UTF_8));
        byte[] content = buildRawZip(List.of(
                zipEntry("manifest.json", manifestBytes("pkg", "svc", sections, Map.of(), Map.of())),
                zipEntry("checksums.json", checksumsBytes(sections, Map.of())),
                zipEntry(TIMELINE, "{}".getBytes(StandardCharsets.UTF_8)),
                zipEntry(REDACTION, "{\"logs\":0}".getBytes(StandardCharsets.UTF_8)),
                zipEntry("extra.json", "{}".getBytes(StandardCharsets.UTF_8))));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("undeclared");
    }

    @Test
    @DisplayName("条目 checksum 不一致拒绝")
    void checksumMismatchRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(TIMELINE, "{\"events\":[\"a\"]}".getBytes(StandardCharsets.UTF_8));
        byte[] content = packageWithSections("pkg", "svc", sections, Map.of(TIMELINE, "0".repeat(64)), Map.of());

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("checksum");
    }

    @Test
    @DisplayName("条目 size 不一致拒绝")
    void sizeMismatchRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(TIMELINE, "{\"events\":[\"a\"]}".getBytes(StandardCharsets.UTF_8));
        byte[] content = packageWithSections("pkg", "svc", sections, Map.of(), Map.of(TIMELINE, 999L));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("size");
    }

    @Test
    @DisplayName("redaction-report 文本值拒绝（严格 schema）")
    void redactionReportTextValueRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(REDACTION, "{\"logs\":\"secret\"}".getBytes(StandardCharsets.UTF_8));
        byte[] content = packageWithSections("pkg", "svc", sections, Map.of(), Map.of());

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("non-negative");
    }

    @Test
    @DisplayName("redaction-report 凭据 key 拒绝（不豁免凭据扫描）")
    void redactionReportCredentialKeyRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(REDACTION, "{\"access_key\":3}".getBytes(StandardCharsets.UTF_8));
        byte[] content = packageWithSections("pkg", "svc", sections, Map.of(), Map.of());

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class).hasMessageContaining("credentials");
    }

    @Test
    @DisplayName("redaction-report 藏 AK/SK/password 值拒绝")
    void redactionReportCredentialValueRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(REDACTION, "{\"password\":\"AKIAABCDEFGHIJKLMNOP\"}".getBytes(StandardCharsets.UTF_8));
        byte[] content = packageWithSections("pkg", "svc", sections, Map.of(), Map.of());

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("manifest 身份不匹配拒绝")
    void manifestIdentityMismatchRejected() throws IOException {
        Map<String, byte[]> sections = new LinkedHashMap<>();
        sections.put(TIMELINE, "{}".getBytes(StandardCharsets.UTF_8));
        byte[] content = packageWithSections("other-pkg", "svc", sections, Map.of(), Map.of());

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("累计解压字节超限拒绝（zip bomb 防护）")
    void decompressedSizeOverLimitRejected() throws IOException {
        properties.setMaxBytes(100);
        byte[] content = buildRawZip(List.of(zipEntry(TIMELINE, new byte[200])));

        Throwable throwable = catchThrowable(() -> validator.validate(content, "svc", "pkg"));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    private byte[] packageWithSections(String packageId, String service, Map<String, byte[]> sections,
            Map<String, String> checksumOverrides, Map<String, Long> sizeOverrides) throws IOException {
        List<Map.Entry<String, byte[]>> entries = new ArrayList<>();
        entries.add(zipEntry("manifest.json", manifestBytes(packageId, service, sections, checksumOverrides, sizeOverrides)));
        entries.add(zipEntry("checksums.json", checksumsBytes(sections, checksumOverrides)));
        for (Map.Entry<String, byte[]> section : sections.entrySet()) {
            entries.add(zipEntry(section.getKey(), section.getValue()));
        }
        return buildRawZip(entries);
    }

    private byte[] manifestBytes(String packageId, String service, Map<String, byte[]> sections,
            Map<String, String> checksumOverrides, Map<String, Long> sizeOverrides) {
        StringBuilder json = new StringBuilder("{\"schemaVersion\":1,\"packageId\":\"");
        json.append(packageId).append("\",\"service\":\"").append(service);
        json.append("\",\"environment\":\"prod\",\"release\":\"1.0.0\",\"commit\":\"abc123\",");
        json.append("\"timeRange\":\"1h\",\"entries\":[");
        int index = 0;
        for (Map.Entry<String, byte[]> section : sections.entrySet()) {
            if (index > 0) {
                json.append(',');
            }
            String path = section.getKey();
            String checksum = checksumOverrides.getOrDefault(path, sha256(section.getValue()));
            long size = sizeOverrides.getOrDefault(path, (long) section.getValue().length);
            json.append("{\"path\":\"").append(path).append("\",\"checksum\":\"").append(checksum);
            json.append("\",\"size\":").append(size).append(",\"category\":\"section\"}");
            index++;
        }
        json.append("]}");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] checksumsBytes(Map<String, byte[]> sections, Map<String, String> checksumOverrides) {
        StringBuilder json = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<String, byte[]> section : sections.entrySet()) {
            if (index > 0) {
                json.append(',');
            }
            String checksum = checksumOverrides.getOrDefault(section.getKey(), sha256(section.getValue()));
            json.append('\"').append(section.getKey()).append("\":\"").append(checksum).append('\"');
            index++;
        }
        json.append('}');
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildRawZip(List<Map.Entry<String, byte[]>> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : entries) {
            offsets.add(writeLocalEntry(output, entry.getKey(), entry.getValue()));
        }
        int centralOffset = output.size();
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, byte[]> entry = entries.get(index);
            writeCentralEntry(output, entry.getKey(), entry.getValue(), offsets.get(index));
        }
        int centralSize = output.size() - centralOffset;
        writeEndRecord(output, entries.size(), centralSize, centralOffset);
        return output.toByteArray();
    }

    private int writeLocalEntry(ByteArrayOutputStream output, String name, byte[] data) throws IOException {
        int offset = output.size();
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        long checksum = crc32(data);
        output.write(intToBytes(0x04034b50));
        output.write(shortToBytes(20));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(intToBytes((int) checksum));
        output.write(intToBytes(data.length));
        output.write(intToBytes(data.length));
        output.write(shortToBytes(nameBytes.length));
        output.write(shortToBytes(0));
        output.write(nameBytes);
        output.write(data);
        return offset;
    }

    private void writeCentralEntry(ByteArrayOutputStream output, String name, byte[] data, int localOffset)
            throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        long checksum = crc32(data);
        output.write(intToBytes(0x02014b50));
        output.write(shortToBytes(20));
        output.write(shortToBytes(20));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(intToBytes((int) checksum));
        output.write(intToBytes(data.length));
        output.write(intToBytes(data.length));
        output.write(shortToBytes(nameBytes.length));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(intToBytes(0));
        output.write(intToBytes(localOffset));
        output.write(nameBytes);
    }

    private void writeEndRecord(ByteArrayOutputStream output, int entryCount, int centralSize, int centralOffset)
            throws IOException {
        output.write(intToBytes(0x06054b50));
        output.write(shortToBytes(0));
        output.write(shortToBytes(0));
        output.write(shortToBytes(entryCount));
        output.write(shortToBytes(entryCount));
        output.write(intToBytes(centralSize));
        output.write(intToBytes(centralOffset));
        output.write(shortToBytes(0));
    }

    private long crc32(byte[] data) {
        CRC32 checksum = new CRC32();
        checksum.update(data);
        return checksum.getValue();
    }

    private byte[] intToBytes(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)};
    }

    private byte[] shortToBytes(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8)};
    }

    private Map.Entry<String, byte[]> zipEntry(String name, byte[] content) {
        return Map.entry(name, content);
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
