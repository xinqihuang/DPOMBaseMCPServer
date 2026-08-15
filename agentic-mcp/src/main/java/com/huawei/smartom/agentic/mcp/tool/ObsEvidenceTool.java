/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.obs.ObsEvidenceService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 封装受控 OBS 证据转移能力的 MCP 工具（put / head / get）。
 *
 * <p>使用独立 gate {@code dpom.obs.transfer-tools-enabled}（不复用 write-tools-enabled / action-enabled）。
 * 上传审批不在此暴露：审批必须由不可被 LLM 调用的可信控制面（内部 REST 审批控制面）触发。
 *
 * @author h00884391
 * @since 2026-08-15
 */
@Component
@ConditionalOnProperty(prefix = "dpom.obs", name = "transfer-tools-enabled", havingValue = "true")
public class ObsEvidenceTool implements McpTool {

    private final ObsEvidenceService service;

    /**
     * 构造 {@code ObsEvidenceTool} 实例。
     *
     * @param service OBS 证据转移编排服务
     */
    public ObsEvidenceTool(ObsEvidenceService service) {
        this.service = service;
    }

    /**
     * 上传证据包到受控 OBS（需可信控制面已审批，校验大小、checksum 与证据包结构）。
     *
     * @param serviceCode      服务编码
     * @param investigationId  调查编号
     * @param packageId        证据包编号
     * @param contentBase64    证据包内容的 Base64 编码
     * @param sha256           证据包 SHA-256 校验和（十六进制）
     * @return 上传结果（objectKey / etag / size）
     */
    @Tool(name = "put_evidence_package", description = "Upload a Diagnostic Evidence Package to the controlled OBS "
            + "location. Requires a prior trusted-control-plane approval for the same identity. Bucket/prefix are "
            + "server-side; the object key is server-generated (identity + content checksum). Content is size-limited, "
            + "its SHA-256 is verified, and the package is parsed and validated (no source code or credentials).")
    public Object putEvidencePackage(
            @ToolParam(description = "Service code, matching [a-zA-Z0-9_-]+") String serviceCode,
            @ToolParam(description = "Investigation id, matching [a-zA-Z0-9_-]+") String investigationId,
            @ToolParam(description = "Package id, matching [a-zA-Z0-9_-]+") String packageId,
            @ToolParam(description = "Base64-encoded package content") String contentBase64,
            @ToolParam(description = "SHA-256 checksum of the package (lowercase hex)") String sha256) {
        return ToolCallSupport.execute("put_evidence_package",
                () -> service.putEvidence(serviceCode, investigationId, packageId, contentBase64, sha256));
    }

    /**
     * 获取证据包对象元数据（head）。
     *
     * @param serviceCode      服务编码
     * @param investigationId  调查编号
     * @param packageId        证据包编号
     * @param sha256           证据包 SHA-256 校验和（十六进制）
     * @return 对象元数据（objectKey / contentLength / etag）
     */
    @Tool(name = "head_evidence_package", description = "Get metadata (content length + etag) of a stored "
            + "Diagnostic Evidence Package. Object key is derived server-side from the identity and content checksum.")
    public Object headEvidencePackage(
            @ToolParam(description = "Service code, matching [a-zA-Z0-9_-]+") String serviceCode,
            @ToolParam(description = "Investigation id, matching [a-zA-Z0-9_-]+") String investigationId,
            @ToolParam(description = "Package id, matching [a-zA-Z0-9_-]+") String packageId,
            @ToolParam(description = "SHA-256 checksum of the package (hex)") String sha256) {
        return ToolCallSupport.execute("head_evidence_package",
                () -> service.headEvidence(serviceCode, investigationId, packageId, sha256));
    }

    /**
     * 获取证据包对象内容（get，限流且有最大读取上限）。
     *
     * @param serviceCode      服务编码
     * @param investigationId  调查编号
     * @param packageId        证据包编号
     * @param sha256           证据包 SHA-256 校验和（十六进制）
     * @return 对象内容（objectKey / content / etag）
     */
    @Tool(name = "get_evidence_package", description = "Download a stored Diagnostic Evidence Package. Object key "
            + "is derived server-side from the identity and content checksum.")
    public Object getEvidencePackage(
            @ToolParam(description = "Service code, matching [a-zA-Z0-9_-]+") String serviceCode,
            @ToolParam(description = "Investigation id, matching [a-zA-Z0-9_-]+") String investigationId,
            @ToolParam(description = "Package id, matching [a-zA-Z0-9_-]+") String packageId,
            @ToolParam(description = "SHA-256 checksum of the package (hex)") String sha256) {
        return ToolCallSupport.execute("get_evidence_package",
                () -> service.getEvidence(serviceCode, investigationId, packageId, sha256));
    }
}
