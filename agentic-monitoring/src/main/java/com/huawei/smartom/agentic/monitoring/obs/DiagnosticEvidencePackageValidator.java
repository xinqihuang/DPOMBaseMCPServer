/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.obs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Diagnostic Evidence Package 结构与内容校验器。
 *
 * <p>要求内容为合法 ZIP，且条目集合严格等于 {@code manifest.json} + {@code checksums.json} + manifest 声明的
 * payload 条目（每条目 checksum/size 与实际内容一致）；manifest 使用 camelCase 字段（与 DPOMAgent
 * PackageSerializer 产物一致）；拒绝重复条目、路径穿越（含反斜杠 / Windows 盘符 / UNC）、源码与凭据；限制条目数与
 * 累计解压字节，完整扫描每个条目（UTF-8 确定性）。{@code security/redaction-report.json} 不豁免凭据扫描，而是做
 * 严格 JSON schema（仅允许 section→非负整数计数）。
 *
 * @author h00884391
 * @since 2026-08-15
 */
@Component
public class DiagnosticEvidencePackageValidator {

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String CHECKSUMS_ENTRY = "checksums.json";
    private static final String REDACTION_REPORT_ENTRY = "security/redaction-report.json";
    private static final int SUPPORTED_SCHEMA = 1;
    private static final int BUFFER_SIZE = 8192;

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".go", ".c", ".cpp", ".cc", ".h", ".hpp",
            ".cs", ".js", ".ts", ".jsx", ".tsx", ".scala", ".rb", ".php",
            ".sh", ".swift", ".rs", ".dart", ".vue", ".sql");

    private static final List<String> CREDENTIAL_MARKERS = List.of(
            "access_key", "secret_key", "secret_access", "password", "api_key",
            "private key", "akia", "-----begin", "client_secret");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ObsProperties properties;

    /**
     * 构造 {@code DiagnosticEvidencePackageValidator}。
     *
     * @param properties OBS 服务端配置（max-bytes / max-entries 上限）
     */
    public DiagnosticEvidencePackageValidator(ObsProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验证据包结构：合法 ZIP + 唯一 manifest.json + 身份匹配 + 条目集合/checksum/size 一致 + 无源码 + 无凭据。
     *
     * @param content     证据包字节内容，不可为 null
     * @param serviceCode 期望的服务编码
     * @param packageId   期望的证据包编号
     * @throws InvalidParamException 结构或内容违反约束时抛出
     */
    public void validate(byte[] content, String serviceCode, String packageId) {
        Map<String, byte[]> entries = readEntries(content);
        validateStructure(entries, serviceCode, packageId);
    }

    private Map<String, byte[]> readEntries(byte[] content) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        int entryCount = 0;
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > properties.getMaxEntries()) {
                    throw new InvalidParamException("evidence package entry count exceeds limit");
                }
                String name = normalize(entry.getName());
                rejectPathTraversal(name);
                if (isSourceFile(name)) {
                    throw new InvalidParamException("source code is not allowed in evidence package: " + name);
                }
                if (entries.containsKey(name)) {
                    throw new InvalidParamException("duplicate entry is not allowed: " + name);
                }
                byte[] entryBytes = readEntryBounded(zip);
                totalBytes += entryBytes.length;
                if (totalBytes > properties.getMaxBytes()) {
                    throw new InvalidParamException("evidence package decompressed size exceeds limit");
                }
                entries.put(name, entryBytes);
                zip.closeEntry();
            }
        }
        catch (InvalidParamException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new InvalidParamException("not a valid diagnostic evidence package zip");
        }
        return entries;
    }

    private void validateStructure(Map<String, byte[]> entries, String serviceCode, String packageId) {
        byte[] manifestBytes = requireEntry(entries, MANIFEST_ENTRY);
        Map<String, String> declaredChecksums = new LinkedHashMap<>();
        Map<String, Long> declaredSizes = new LinkedHashMap<>();
        validateManifest(manifestBytes, serviceCode, packageId, declaredChecksums, declaredSizes);
        validateChecksums(requireEntry(entries, CHECKSUMS_ENTRY), declaredChecksums);
        validateEntrySet(entries, declaredChecksums);
        validateContents(entries, declaredChecksums, declaredSizes);
    }

    private byte[] requireEntry(Map<String, byte[]> entries, String name) {
        byte[] content = entries.get(name);
        if (content == null) {
            throw new InvalidParamException("evidence package is missing " + name);
        }
        return content;
    }

    private void validateManifest(byte[] manifestBytes, String serviceCode, String packageId,
            Map<String, String> declaredChecksums, Map<String, Long> declaredSizes) {
        JsonNode manifest = readJson(manifestBytes, MANIFEST_ENTRY);
        if (!isSupportedSchema(manifest)) {
            throw new InvalidParamException("unsupported evidence package schema version");
        }
        if (!packageId.equals(textField(manifest, "packageId"))) {
            throw new InvalidParamException("manifest packageId does not match request");
        }
        if (!serviceCode.equals(textField(manifest, "service"))) {
            throw new InvalidParamException("manifest service does not match request");
        }
        if (isBlank(textField(manifest, "release")) || isBlank(textField(manifest, "commit"))) {
            throw new InvalidParamException("manifest release and commit are required");
        }
        JsonNode entries = manifest.get("entries");
        if (entries == null || !entries.isArray()) {
            throw new InvalidParamException("manifest entries are required");
        }
        for (JsonNode entry : entries) {
            collectDeclaredEntry(entry, declaredChecksums, declaredSizes);
        }
    }

    private void collectDeclaredEntry(JsonNode entry, Map<String, String> checksums, Map<String, Long> sizes) {
        String path = textField(entry, "path");
        String checksum = textField(entry, "checksum");
        JsonNode sizeNode = entry.get("size");
        boolean invalid = path.isBlank() || !isSha256Hex(checksum) || sizeNode == null
                || !sizeNode.isIntegralNumber() || sizeNode.asLong() < 0;
        if (invalid) {
            throw new InvalidParamException("manifest entry is invalid");
        }
        if (checksums.containsKey(path)) {
            throw new InvalidParamException("duplicate manifest entry path: " + path);
        }
        checksums.put(path, checksum);
        sizes.put(path, sizeNode.asLong());
    }

    private void validateChecksums(byte[] checksumsBytes, Map<String, String> declaredChecksums) {
        JsonNode checksums = readJson(checksumsBytes, CHECKSUMS_ENTRY);
        if (!checksums.isObject() || checksums.size() != declaredChecksums.size()) {
            throw new InvalidParamException("checksums.json does not match manifest entries");
        }
        Iterator<String> fieldNames = checksums.fieldNames();
        while (fieldNames.hasNext()) {
            String path = fieldNames.next();
            JsonNode value = checksums.get(path);
            String declared = declaredChecksums.get(path);
            if (declared == null || !value.isTextual() || !declared.equals(value.asText())) {
                throw new InvalidParamException("checksums.json does not match manifest entries");
            }
        }
    }

    private void validateEntrySet(Map<String, byte[]> entries, Map<String, String> declaredChecksums) {
        Set<String> allowed = new HashSet<>();
        allowed.add(MANIFEST_ENTRY);
        allowed.add(CHECKSUMS_ENTRY);
        allowed.addAll(declaredChecksums.keySet());
        if (entries.size() != allowed.size()) {
            throw new InvalidParamException("evidence package contains undeclared entries");
        }
        for (String name : entries.keySet()) {
            if (!allowed.contains(name)) {
                throw new InvalidParamException("evidence package contains undeclared entry: " + name);
            }
        }
    }

    private void validateContents(Map<String, byte[]> entries, Map<String, String> declaredChecksums,
            Map<String, Long> declaredSizes) {
        for (Map.Entry<String, byte[]> item : entries.entrySet()) {
            String name = item.getKey();
            if (MANIFEST_ENTRY.equals(name) || CHECKSUMS_ENTRY.equals(name)) {
                continue;
            }
            if (REDACTION_REPORT_ENTRY.equals(name)) {
                validateRedactionReport(item.getValue());
            }
            rejectCredentials(item.getValue(), name);
        }
        for (Map.Entry<String, String> declared : declaredChecksums.entrySet()) {
            String path = declared.getKey();
            byte[] actual = entries.get(path);
            if (actual == null) {
                throw new InvalidParamException("manifest declares missing entry: " + path);
            }
            if (!declared.getValue().equals(sha256Hex(actual))) {
                throw new InvalidParamException("entry checksum mismatch: " + path);
            }
            if (actual.length != declaredSizes.get(path).longValue()) {
                throw new InvalidParamException("entry size mismatch: " + path);
            }
        }
    }

    private void validateRedactionReport(byte[] content) {
        JsonNode report = readJson(content, REDACTION_REPORT_ENTRY);
        if (!report.isObject()) {
            throw new InvalidParamException("redaction report must be an object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = report.fields();
        while (fields.hasNext()) {
            JsonNode value = fields.next().getValue();
            if (!value.isIntegralNumber() || value.asLong() < 0) {
                throw new InvalidParamException("redaction report counts must be non-negative integers");
            }
        }
    }

    private JsonNode readJson(byte[] content, String entryName) {
        try {
            return mapper.readTree(content);
        }
        catch (IOException exception) {
            throw new InvalidParamException(entryName + " is not valid JSON");
        }
    }

    private boolean isSupportedSchema(JsonNode manifest) {
        JsonNode node = manifest.get("schemaVersion");
        return node != null && node.canConvertToInt() && node.asInt() == SUPPORTED_SCHEMA;
    }

    private String textField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? "" : value.asText();
    }

    private boolean isSha256Hex(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void rejectCredentials(byte[] content, String entryName) {
        String lower = new String(content, StandardCharsets.UTF_8).toLowerCase();
        for (String marker : CREDENTIAL_MARKERS) {
            if (lower.contains(marker)) {
                throw new InvalidParamException("credentials are not allowed in evidence package: " + entryName);
            }
        }
    }

    private void rejectPathTraversal(String name) {
        boolean absolute = name.startsWith("/") || name.startsWith("..") || name.contains("/..");
        boolean drive = name.length() >= 2 && isAsciiLetter(name.charAt(0)) && name.charAt(1) == ':';
        if (absolute || drive) {
            throw new InvalidParamException("path traversal is not allowed: " + name);
        }
    }

    private boolean isAsciiLetter(char character) {
        return (character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z');
    }

    private String normalize(String name) {
        return name.replace('\\', '/');
    }

    private byte[] readEntryBounded(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (output.size() + read > properties.getMaxBytes()) {
                throw new InvalidParamException("evidence package entry exceeds size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean isSourceFile(String entryName) {
        String lower = entryName.toLowerCase();
        for (String extension : SOURCE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
