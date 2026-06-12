/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailRequest;
import com.huawei.smartom.agentic.monitoring.apm.ApmTraceService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code show_clob_detail} 能力的 MCP 工具：按 clob 引用取回超长字段全文。
 *
 * <p>T29 根因诊断链第 5 步（终点），工具命名、描述及语义遵循
 * {@code docs/specs/tools/show_clob_detail.md}。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@Component
public class ApmClobDetailTool implements McpTool {

    private final ApmTraceService service;

    /**
     * 构造 {@code ApmClobDetailTool} 实例。
     *
     * @param service APM 调用链查询服务
     */
    public ApmClobDetailTool(ApmTraceService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按 clob_id 取回全文。
     *
     * @param businessId 应用 id，可选（回落服务端配置）
     * @param envId      事件所属环境 id
     * @param clobId     clob 引用 id
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmClobDetailResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "show_clob_detail",
            description = """
                    Fetch the FULL text of an oversized event field — complete exception stack \
                    trace or complete SQL statement. Event details truncate such values and \
                    expose them as clob references; pass that clob_id here together with the \
                    event's env_id. clob_id MUST come from a show_event_detail / \
                    show_trace_events response (tags or attachment values) — do NOT invent it. \
                    Live data, not cached.""")
    public Object showClobDetail(
            @ToolParam(description = "APM business id; falls back to the server-configured "
                    + "default when omitted.", required = false)
            Long businessId,
            @ToolParam(description = "Environment id of the event the clob belongs to.")
            Long envId,
            @ToolParam(description = "Clob reference id from the event's tags/attachment.")
            String clobId) {
        ApmClobDetailRequest req = new ApmClobDetailRequest(businessId, envId, clobId);
        return ToolCallSupport.execute("show_clob_detail", () -> service.showClobDetail(req));
    }
}
