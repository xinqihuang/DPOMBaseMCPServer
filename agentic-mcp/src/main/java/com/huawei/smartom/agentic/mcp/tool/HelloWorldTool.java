/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026, All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Trivial MCP tool used to verify the server is reachable.
 *
 * <p>Kept until at least one real business tool is wired up and proven working.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@Component
public class HelloWorldTool {

    /**
     * Returns a greeting string used to verify that the MCP server is reachable and tool
     * dispatch is wired up correctly.
     *
     * @param name the name to greet; when {@code null} or blank, {@code "World"} is used
     * @return a greeting in the form {@code "Hello, <name>!"}
     */
    @Tool(name = "hello_world",
          description = "Test tool. Returns a greeting. Used to verify the MCP server is reachable.")
    public String helloWorld(
            @ToolParam(description = "Name to greet") String name) {
        String safeName = (name == null || name.isBlank()) ? "World" : name;
        return "Hello, " + safeName + "!";
    }
}
