/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

/**
 * MCP 工具组件的标记接口。
 *
 * <p>所有被 {@code @Component} 标注、希望注册到 MCP Server 的工具类都应实现本接口。
 * {@code McpServerConfig} 会通过 Spring 的集合注入自动收集全部实现并完成注册，
 * 因此新增工具时无需再改动注册配置。
 *
 * @author h00884391
 * @since 2026-06-12
 */
public interface McpTool {
}
