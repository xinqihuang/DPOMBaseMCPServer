package com.huawei.smartom.agentic.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Trivial MCP tool used to verify the server is reachable.
 *
 * <p>Kept until at least one real business tool is wired up and proven working.
 */
@Component
public class HelloWorldTool {

    @Tool(name = "hello_world",
          description = "Test tool. Returns a greeting. Used to verify the MCP server is reachable.")
    public String helloWorld(
            @ToolParam(description = "Name to greet") String name) {
        String safeName = (name == null || name.isBlank()) ? "World" : name;
        return "Hello, " + safeName + "!";
    }
}
