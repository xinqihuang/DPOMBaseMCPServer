/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationRequest;
import com.huawei.smartom.agentic.monitoring.apm.ApmDiscoveryService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code search_apm_application} 能力的 MCP 工具：搜索应用下的组件/环境及其探针情况。
 *
 * <p>APM CMDB 发现链第 1 步（T28），工具命名、描述及参数语义遵循
 * {@code docs/specs/tools/search_apm_application.md}。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@Component
public class ApmApplicationTool {

    private final ApmDiscoveryService service;

    /**
     * 构造 {@code ApmApplicationTool} 实例。
     *
     * @param service APM 发现编排服务
     */
    public ApmApplicationTool(ApmDiscoveryService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：按应用（business）搜索组件与环境，返回每个环境的探针在线情况。
     *
     * @param businessId 应用 id，取自 {@code list_apm_business}；可选（回落服务端配置）
     * @param region     区域名称，可选（回落服务端配置）
     * @param page       页码（从 1 开始），可选（默认 1）
     * @param pageSize   每页条数，可选
     * @param keyword    组件/环境名称关键字，可选
     * @return 成功时返回
     *         {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "search_apm_application",
            description = """
                    Step 1 of the APM discovery chain: search the components (apps) and \
                    environments under one APM application (business), including per-environment \
                    probe status (online / manually-stopped / offline counts). business_id MUST \
                    come from a prior list_apm_business response — do NOT invent it; omit it to \
                    use the server-configured default. Each result's env_id feeds \
                    show_env_monitor_items (next step of the chain). Probe counts are live \
                    operational state, so results are NOT cached.""")
    public Object searchApmApplication(
            @ToolParam(description = "APM business id from list_apm_business. Falls back to the "
                    + "server-configured default when omitted.", required = false)
            Long businessId,
            @ToolParam(description = "Region name, e.g. cn-north-4. Falls back to the "
                    + "server-configured APM region when omitted.", required = false)
            String region,
            @ToolParam(description = "Page number starting from 1, default 1.", required = false)
            Integer page,
            @ToolParam(description = "Page size, >= 1.", required = false)
            Integer pageSize,
            @ToolParam(description = "Keyword filter on component/environment names.",
                    required = false)
            String keyword) {

        ApmSearchApplicationRequest req = new ApmSearchApplicationRequest(
                businessId, region, page, pageSize, keyword);
        return ToolCallSupport.execute("search_apm_application", () -> service.searchApplication(req));
    }
}
