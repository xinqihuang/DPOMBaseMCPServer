/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.monitoring.apm.ApmDiscoveryService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 封装 {@code list_apm_business} 能力的 MCP 工具：查询租户全部 APM 应用（business）列表。
 *
 * <p>APM CMDB 发现链第 0 步（T28），工具命名、描述及语义遵循
 * {@code docs/specs/tools/list_apm_business.md}。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@Component
public class ApmBusinessTool implements McpTool {

    private final ApmDiscoveryService service;

    /**
     * 构造 {@code ApmBusinessTool} 实例。
     *
     * @param service APM 发现编排服务（含 Caffeine 缓存）
     */
    public ApmBusinessTool(ApmDiscoveryService service) {
        this.service = service;
    }

    /**
     * MCP 入口方法：列出当前租户在 APM 注册的全部应用。
     *
     * @return 成功时返回 {@link com.huawei.smartom.agentic.adapter.apm.dto.ApmListBusinessResponse}，
     *         失败时返回 {@link com.huawei.smartom.agentic.common.error.ErrorResponse}
     */
    @Tool(
            name = "list_apm_business",
            description = """
                    Step 0 of the APM discovery chain: list ALL APM applications (businesses) \
                    registered for the current tenant in Huawei Cloud APM. Each entry's 'id' is \
                    the business_id required by every other APM tool — get it from here, do NOT \
                    invent it. Call order: list_apm_business -> search_apm_application (gives \
                    env_id) -> show_env_monitor_items -> show_apm_monitor_item_view_config -> \
                    show_apm_trend. Takes no parameters; results are cached server-side for \
                    about 1 day.""")
    public Object listApmBusiness() {
        return ToolCallSupport.execute("list_apm_business", service::listBusiness);
    }
}
