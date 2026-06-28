> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T01-bootstrap.md`，状态 Ready→Done）。已交付项勾选 `[x]`；原任务卡「不做（本期未交付）」项列在末尾未勾选。

## 1. Parent pom 与工程规范

- [x] 1.1 Parent `pom.xml`：`java.version=21`、`maven.compiler.release=21`、`<modules>` 列六个目录名（`agentic-common` / `agentic-adapter-ces` / `agentic-adapter-aom` / `agentic-adapter-apm` / `agentic-monitoring` / `agentic-mcp`）
- [x] 1.2 `dependencyManagement` 引入 `spring-boot-dependencies` 3.4.x BOM、`spring-ai-bom:1.0.4`（`huaweicloud-sdk-bom:3.1.196` 如可用纳入管理，本期不引具体 SDK）
- [x] 1.3 插件管理：`maven-compiler-plugin` / `maven-surefire-plugin` / `maven-checkstyle-plugin`（`mvn validate` 自动跑 checkstyle）
- [x] 1.4 `.editorconfig` + `checkstyle.xml`（行宽 120、4 空格缩进、强制 `{}`、禁 import *、import 分组遵循 CLAUDE.md §3.1）
- [x] 1.5 `.gitignore` + `README.md` 骨架

## 2. 子模块骨架

- [x] 2.1 `agentic-common` pom + `com/huawei/smartom/agentic/common/.gitkeep`
- [x] 2.2 `agentic-adapter-ces` pom + `.../adapter/ces/.gitkeep`
- [x] 2.3 `agentic-adapter-aom` pom + `.../adapter/aom/.gitkeep`
- [x] 2.4 `agentic-adapter-apm` pom + `.../adapter/apm/.gitkeep`
- [x] 2.5 `agentic-monitoring` pom + `.../monitoring/.gitkeep`
- [x] 2.6 `agentic-mcp` pom（接入 `spring-ai-starter-mcp-server-webmvc`、`logstash-logback-encoder:7.4`、actuator/micrometer-prometheus、resilience4j）

## 3. 启动入口与配置（agentic-mcp）

- [x] 3.1 `DpomBaseMcpServerApplication`：`@SpringBootApplication(scanBasePackages="com.huawei.smartom.agentic")`
- [x] 3.2 `config/HuaweiCloudProperties`：`@ConfigurationProperties(prefix="huaweicloud")`（region / ak / sk + getter/setter，不用 Lombok）
- [x] 3.3 `config/McpServerConfig`：`ToolCallbackProvider` bean 用 `MethodToolCallbackProvider` 注册 `HelloWorldTool`
- [x] 3.4 `application.yml`：MCP server（name/version/type=SYNC、sse-endpoint `/sse`、sse-message-endpoint `/mcp/messages`）、虚拟线程、server 8080、management 8081、actuator 暴露 health/info/metrics/prometheus + readiness group 占位、`huaweicloud` ak/sk 取环境变量、Resilience4j 限流（ces/aom/apm-readonly 各 10 QPS）+ 重试（huaweicloud-retryable）占位
- [x] 3.5 `application-local.yml`
- [x] 3.6 `logback-spring.xml`：`LogstashEncoder` JSON → stdout，MDC `traceId` / `upstreamTraceId` / `tool`

## 4. 验证用测试 tool

- [x] 4.1 `tool/HelloWorldTool`：`@Tool` 标注 `helloWorld(@ToolParam String name) -> "Hello, {name}!"`
- [x] 4.2 `DpomBaseMcpServerApplicationTests`：`@SpringBootTest` 启动测试

## 5. 验收

- [x] 5.1 `mvn clean install` 成功；六模块全部 build 通过
- [x] 5.2 `mvn checkstyle:check` 0 violations
- [x] 5.3 `mvn spring-boot:run -pl agentic-mcp` 启动成功，日志可见 JSON 格式
- [x] 5.4 `/actuator/health` 返回 UP；`/actuator/prometheus` 有数据
- [x] 5.5 MCP Inspector 连 `http://localhost:8080/sse` 见 `hello_world`，调用返回 `"Hello, World!"`

## 6. 遗留项（本期未交付）

- [ ] 6.1 CI/CD 配置（属 T02）
- [ ] 6.2 Helm Chart（属 T02）
- [ ] 6.3 任何业务 adapter / tool（属 T03+）
