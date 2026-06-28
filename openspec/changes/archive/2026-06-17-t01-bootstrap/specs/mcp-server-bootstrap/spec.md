## ADDED Requirements

### Requirement: MCP Server 宿主可运行

系统 SHALL 提供一个基于 Spring Boot + Spring AI 的 MCP Server 宿主进程：以 `DpomBaseMcpServerApplication` 为启动入口，接入 `spring-ai-starter-mcp-server-webmvc`，通过 SSE endpoint `/sse`（消息路径 `/mcp/messages`）对外暴露 MCP 能力。启动类 MUST 使用 `@SpringBootApplication(scanBasePackages="com.huawei.smartom.agentic")` 以扫描全部子模块 bean。本变更不交付任何业务工具能力。

#### Scenario: 本地启动并连接
- **WHEN** 在本地执行 `mvn spring-boot:run -pl agentic-mcp`
- **THEN** 进程 SHALL 启动成功并监听服务端口 8080
- **AND** MCP Inspector 连接 `http://localhost:8080/sse` SHALL 成功建立会话

#### Scenario: 多模块构建
- **WHEN** 执行 `mvn clean install`
- **THEN** 六个子模块（`agentic-common` / `agentic-adapter-ces` / `agentic-adapter-aom` / `agentic-adapter-apm` / `agentic-monitoring` / `agentic-mcp`）SHALL 全部构建通过

### Requirement: 链路自检工具

系统 SHALL 提供一个仅用于验证 MCP 工具暴露链路的临时测试工具 `hello_world`，经 `McpServerConfig` 以 `MethodToolCallbackProvider` 注册。该工具不属于业务能力，SHALL 在后续业务工具接入后可被移除。

#### Scenario: hello_world 可被发现与调用
- **GIVEN** MCP Server 已启动且客户端已连接
- **WHEN** 客户端列出工具并以 `name="World"` 调用 `hello_world`
- **THEN** 工具列表 SHALL 包含 `hello_world`
- **AND** 调用 SHALL 返回 `"Hello, World!"`

### Requirement: 配置与可观测基线

系统 SHALL 提供统一的配置、日志与可观测基线：`HuaweiCloudProperties` 绑定 `huaweicloud` 前缀（`region` / `ak` / `sk`，ak/sk 取自环境变量 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK`）；Logback 经 `logback-spring.xml` 用 `LogstashEncoder` 以 JSON 输出到 stdout 并预留 MDC 字段 `traceId` / `upstreamTraceId` / `tool`；Actuator 在独立管理端口 8081 暴露 `health` / `info` / `metrics` / `prometheus`。Resilience4j 限流与重试 SHALL 仅以配置占位形式存在，本期不接入调用路径。

#### Scenario: 健康检查与指标可用
- **WHEN** 进程启动后访问 `http://localhost:8081/actuator/health`
- **THEN** 总体健康状态 SHALL 返回 UP
- **AND** `http://localhost:8081/actuator/prometheus` SHALL 返回非空指标数据

#### Scenario: 日志为 JSON 格式
- **WHEN** 进程产生启动日志
- **THEN** 日志 SHALL 以 JSON 格式输出到 stdout
