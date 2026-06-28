## Why

> 存量回填：本变更对应的项目骨架已于早期 commit 交付（原任务卡 `docs/tasks/T01-bootstrap.md`，状态 Ready→Done），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

智能运维 Agent 的全部 MCP 业务工具（CES / AOM / APM 适配）都需要一个统一、可运行、可观测的 Spring Boot + Spring AI MCP Server 宿主进程。T01 提供这一地基：一套多模块 Maven 工程、MCP server starter 接入、统一配置 / 日志 / 健康检查 / 指标，以及限流与重试的占位配置。在没有这层骨架前，任何业务工具（T03+）都无法被装配、暴露与运行，也无法验证 MCP 链路是否打通。

## What Changes

- 搭建 Parent pom：统一 Java 21、`spring-boot-dependencies` 3.4.x BOM、`spring-ai-bom:1.0.4`、插件管理（compiler / surefire / checkstyle），`<modules>` 列六个子模块。
- 新增 6 个子模块骨架：`agentic-common` / `agentic-adapter-ces` / `agentic-adapter-aom` / `agentic-adapter-apm` / `agentic-monitoring` / `agentic-mcp`（仅 pom 与包占位，不含业务）。
- `agentic-mcp` 作为启动入口：`DpomBaseMcpServerApplication`（`@SpringBootApplication(scanBasePackages="com.huawei.smartom.agentic")`）+ `spring-ai-starter-mcp-server-webmvc`（SSE endpoint `/sse`，消息路径 `/mcp/messages`）。
- 配置：`application.yml` / `application-local.yml`（MCP server、Actuator/Micrometer、Resilience4j 占位、`huaweicloud.region` + ak/sk 从环境变量），`HuaweiCloudProperties` (`@ConfigurationProperties(prefix="huaweicloud")`)。
- 可观测：Logback JSON（`LogstashEncoder`）输出到 stdout，MDC 字段 `traceId` / `upstreamTraceId` / `tool`；Actuator 暴露 health/info/metrics/prometheus，独立管理端口 8081。
- 工程规范：`.editorconfig`、`checkstyle.xml`（行宽 120、4 空格缩进、强制 `{}`、禁 import *）、`.gitignore`、`README.md` 骨架。
- 验证用的 `hello_world` 测试 tool（`HelloWorldTool` + `McpServerConfig` 注册），仅用于确认 MCP starter 链路打通，非业务工具。

## Capabilities

### New Capabilities

无业务能力（基础设施变更）。本变更不交付任何 MCP 业务工具。仅记录一项骨架自检 capability 用于规格管理：

- `mcp-server-bootstrap`: MCP Server 宿主进程的可运行性、配置 / 日志 / 可观测基线，以及用于验证 MCP 工具暴露链路的临时 `hello_world` 自检工具（非业务能力，后续业务工具接入后可移除）。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：新增 `agentic-common` / `agentic-adapter-ces` / `agentic-adapter-aom` / `agentic-adapter-apm` / `agentic-monitoring` / `agentic-mcp`（前五个仅骨架，`agentic-mcp` 含启动类 / 配置 / 测试 tool）。
- 配置：`agentic-mcp/src/main/resources/{application.yml,application-local.yml,logback-spring.xml}`；环境变量 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK`。
- 端口：服务端口 8080（含 SSE `/sse`）、管理端口 8081（Actuator/Prometheus）。
- 工程根：`pom.xml`(parent) / `.editorconfig` / `checkstyle.xml` / `.gitignore` / `README.md`。
- 不引入任何华为云 SDK 依赖（属 T04）；不含 CI/CD 与 Helm（属 T02）；不含任何业务 adapter / tool（属 T03+）。
