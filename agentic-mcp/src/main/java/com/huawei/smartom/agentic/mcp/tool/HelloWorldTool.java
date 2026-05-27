/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 用于验证服务器连通性的简易 MCP 工具。
 *
 * <p>在至少有一个真实业务工具接入并验证通过之前保留。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class HelloWorldTool {

    /**
     * 返回问候字符串，用于验证 MCP 服务器可达且工具分发链路正确。
     *
     * @param name 待问候的名称；为 {@code null} 或空白时使用 {@code "World"}
     * @return 形如 {@code "Hello, <name>!"} 的问候语
     */
    @Tool(name = "hello_world",
            description = "Test tool. Returns a greeting. Used to verify the MCP server is reachable.")
    public String helloWorld(
            @ToolParam(description = "Name to greet") String name) {
        String safeName = (name == null || name.isBlank()) ? "World" : name;
        return "Hello, " + safeName + "!";
    }
}
