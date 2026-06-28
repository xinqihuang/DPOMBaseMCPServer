## Context

存量基础设施回填。原始任务卡：`docs/tasks/T01-bootstrap.md`（状态 Ready，估时 0.5d，后置 T02/T03）。本文承载项目骨架的架构决策：多模块布局与依赖方向、MCP starter 接入约定、配置 / 日志 / 可观测约定、限流与重试占位的技术选型，以及 AI 在搭骨架时高频踩坑的细节。这是后续所有业务工具（T03+）的运行宿主，本身不暴露业务能力，故无 delta spec。

## Goals / Non-Goals

**Goals:**
- 提供能本地 `mvn spring-boot:run` 起来、MCP Inspector 可连上 SSE endpoint 的最小可运行宿主。
- 固化六模块多模块工程的依赖方向与版本统一（Java 21 / Spring Boot 3.4.x / Spring AI 1.0.4）。
- 预置统一日志（JSON + MDC）、健康检查、Prometheus 指标、限流 / 重试占位，使后续业务工具开箱即用。
- 用 `hello_world` 测试 tool 验证 MCP starter 的工具暴露链路打通。

**Non-Goals:**
- 任何业务 adapter / tool（CES / AOM / APM）—— 属 T03+。
- 引入华为云 SDK 依赖与凭证健康指标实现 —— 属 T04 / T03。
- CI/CD pipeline 与 Helm Chart —— 属 T02。
- 实际启用 Resilience4j 限流 / 重试（本期仅配置占位，不在代码路径中拦截）。

## Decisions

### 模块布局与依赖方向

- 六个 Maven 子模块（artifactId = 目录名）：`agentic-common`（公共：错误码 / DTO 基类 / 工具方法，本期仅占位）、`agentic-adapter-ces` / `agentic-adapter-aom` / `agentic-adapter-apm`（各云服务 SDK 适配层，本期仅占位）、`agentic-monitoring`（service 编排层，本期仅占位）、`agentic-mcp`（启动入口 + MCP 工具暴露 + 配置）。
- 依赖方向（单向，自上而下）：`agentic-mcp` → `agentic-monitoring` → `agentic-adapter-*` → `agentic-common`。adapter 之间不互相依赖。
- Parent pom 用 `dependencyManagement` 引入 `spring-boot-dependencies` 3.4.x BOM、`spring-ai-bom:1.0.4`，子模块只声明 `groupId/artifactId` 不写版本；`huaweicloud-sdk-bom:3.1.196` 若可用则纳入管理（具体 SDK 依赖在 T04 引入，本期不引）。

### MCP server 接入

- Starter（新名，钉死）：`org.springframework.ai:spring-ai-starter-mcp-server-webmvc`（**不是**旧名 `spring-ai-mcp-server-spring-boot-starter`）。
- 传输：WebMVC + SSE。SSE endpoint 默认 `/sse`，消息路径默认 `/mcp/messages`（`spring.ai.mcp.server.sse-endpoint` / `sse-message-endpoint`）。server 模式 `SYNC`。
- 启动类 `DpomBaseMcpServerApplication` 在包 `com.huawei.smartom.agentic.mcp`，`@SpringBootApplication(scanBasePackages="com.huawei.smartom.agentic")` —— 必须显式指定 scanBasePackages，否则默认只扫启动类所在包及子包，扫不到 `com.huawei.smartom.agentic.adapter.*` 等其他模块的 bean。
- 工具注册：`McpServerConfig` 提供 `ToolCallbackProvider` bean，用 `MethodToolCallbackProvider.builder().toolObjects(...).build()` 把 `@Tool` 标注的组件聚合暴露。本期仅注册 `HelloWorldTool`。

### 配置约定

- `huaweicloud` 前缀绑定到 `HuaweiCloudProperties`（`region` / `ak` / `sk`）。ak/sk 从环境变量读：`huaweicloud.ak: ${HUAWEICLOUD_AK:}` / `huaweicloud.sk: ${HUAWEICLOUD_SK:}`，缺省为空串（本期不校验，不建 SDK client）。默认 region `cn-southwest-2`（贵阳一）。
- 服务端口 8080，管理端口独立 8081；Actuator 暴露 `health,info,metrics,prometheus`，health 开启 probes，readiness group 占位 include `readinessState,huaweiCloudCredentials`（`huaweiCloudCredentials` indicator 在 T03 实现，本期仅配置占位，可能导致 readiness 暂不就绪，属预期）。
- 开启虚拟线程：`spring.threads.virtual.enabled=true`。

### 日志与可观测

- Logback 配置文件名 MUST 为 `logback-spring.xml`（带 `-spring` 后缀），否则 Spring profile 占位符 `<springProfile>` 不生效。
- JSON 编码器：`net.logstash.logback.encoder.LogstashEncoder`（依赖 `net.logstash.logback:logstash-logback-encoder:7.4`），输出到 stdout。
- 预留 MDC 字段：`traceId`（本地链路）、`upstreamTraceId`（华为云 `X-Request-Id`，业务工具回填）、`tool`（当前工具名）。本期无业务工具写入，仅约定字段名。
- 指标：Micrometer + Prometheus（`/actuator/prometheus`）。业务工具的 `mcp_tool_invocation` 指标在各工具卡实现。

### Resilience4j 占位（本期不启用）

- 限流 key（RateLimiter instance）：`ces-readonly` / `aom-readonly` / `apm-readonly`，均 10 QPS（`limit-for-period=10`、`limit-refresh-period=1s`、`timeout-duration=0`）。
- 重试 key（Retry instance）：`huaweicloud-retryable`，`max-attempts=3`、`wait-duration=200ms`、`exponential-backoff-multiplier=4`；具体重试条件（基于 `ErrorCode`：仅 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试）在 Java 配置里指定，本期不写。
- 本期仅 YAML 占位，不接入任何调用路径。

### 工程规范

- Checkstyle（`mvn validate` 自动跑，违规则 build 失败）：行宽 120、缩进 4 空格、if/for/while 强制 `{}`、禁止 `import *`、import 分组顺序遵循 CLAUDE.md §3.1。
- 不使用 Lombok（见 ADR-003）；DTO 用 Java record / 手写 getter/setter。

## Risks / Trade-offs

- **artifactId 与 Java 包名映射易混**：父 pom `<modules>` 写的是目录名 / artifactId（`agentic-adapter-ces` 等），不是 Java 包路径（`com.huawei.smartom.agentic.adapter.ces`）。两者形似但语义不同，AI 高频写错。
- **scanBasePackages 漏配**：若启动类未显式 `scanBasePackages="com.huawei.smartom.agentic"`，其他模块 bean 扫不到，业务工具在 T03+ 接入时会静默不暴露，难排查 —— 故在骨架阶段就定死。
- **readiness 占位 indicator 未就绪**：`huaweiCloudCredentials` 在 readiness group 中占位但本期无实现，可能使 `/actuator/health/readiness` 暂不为 UP；`/actuator/health`（liveness/总览）应为 UP。属预期，T03 补齐。
- **Starter 名称版本敏感**：Spring AI 1.0.4 用新 starter 名 `spring-ai-starter-mcp-server-webmvc`，沿用旧名会找不到 starter；`@Tool` / `@ToolParam` 注解需与该版本匹配。
- **不引华为云 SDK**：本期任何 adapter 模块都不得引入 `huaweicloud-sdk-*` 依赖（属 T04），避免提前耦合版本。
- **logback 文件名陷阱**：用 `logback.xml` 而非 `logback-spring.xml` 会导致 Spring 扩展（profile / property 占位）失效，JSON / profile 行为异常。
