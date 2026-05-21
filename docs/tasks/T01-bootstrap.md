# T01 — 项目骨架

> 状态: Ready · 估时: 0.5d · 依赖: 无 · 后置: T02, T03

## 目标

搭起一个能跑起来的 Spring Boot + Spring AI MCP Server 骨架，**不含任何业务 tool**。验收时：能在本地 `mvn spring-boot:run`，MCP Inspector 能连上 SSE endpoint，看到一个 hello-world 测试 tool。

## 范围

**做**:
- Parent pom（dependencyManagement、插件管理、版本统一）
- 6 个子模块的 pom（仅依赖声明，不写业务）
- Spring Boot 启动类
- Spring AI MCP Server starter 接入
- 基础 `application.yml` + 配置 properties 类
- 日志（Logback JSON 格式 → stdout）
- Actuator + Micrometer 配置
- Resilience4j 配置占位（不实际使用）
- `.editorconfig` + `checkstyle.xml`
- `.gitignore`
- README.md 骨架
- 一个 hello-world 测试 tool（`hello_world(name: String) -> "Hello, {name}"`），用于验证 MCP starter 工作

**不做**:
- CI/CD 配置（在 T02）
- Helm Chart（在 T02）
- 任何业务 adapter / tool（在 T03+）

## 产物清单

```
pom.xml                                                     ← parent
.editorconfig
checkstyle.xml
.gitignore
README.md

agentic-common/
  pom.xml
  src/main/java/com/huawei/smartom/agentic/common/.gitkeep

agentic-adapter-ces/
  pom.xml
  src/main/java/com/huawei/smartom/agentic/adapter/ces/.gitkeep

agentic-adapter-aom/
  pom.xml
  src/main/java/com/huawei/smartom/agentic/adapter/aom/.gitkeep

agentic-adapter-apm/
  pom.xml
  src/main/java/com/huawei/smartom/agentic/adapter/apm/.gitkeep

agentic-monitoring/
  pom.xml
  src/main/java/com/huawei/smartom/agentic/monitoring/.gitkeep

agentic-mcp/
  pom.xml
  src/main/java/com/huawei/smartom/agentic/mcp/
    DpomBaseMcpServerApplication.java                       ← @SpringBootApplication
    config/McpServerConfig.java                             ← MCP server bean 配置
    config/HuaweiCloudProperties.java                       ← @ConfigurationProperties
    tool/HelloWorldTool.java                                ← 测试 tool
  src/main/resources/
    application.yml
    application-local.yml
    logback-spring.xml
  src/test/java/com/huawei/smartom/agentic/mcp/
    DpomBaseMcpServerApplicationTests.java                  ← @SpringBootTest 启动测试
```

## 关键技术要求

### Parent pom

- `<java.version>21</java.version>`
- `<maven.compiler.release>21</maven.compiler.release>`
- 引入 `spring-boot-dependencies` 3.4.x BOM
- 引入 `spring-ai-bom:1.0.4`
- 引入 `huaweicloud-sdk-bom:3.1.196`（如有；否则各 SDK 模块单独写版本）
- 插件：`maven-compiler-plugin`, `maven-surefire-plugin`, `maven-checkstyle-plugin`
- `<modules>` 列六个

### MCP server 模块（启动入口）

- Maven artifactId / 目录名: `agentic-mcp`
- Starter: `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`
- 启动类：`DpomBaseMcpServerApplication`，包路径 `com.huawei.smartom.agentic.mcp`
- 启动类的 `@SpringBootApplication` 用 `scanBasePackages = "com.huawei.smartom.agentic"`（让 Spring 能扫到其他子模块的 bean）

### application.yml 基础结构

```yaml
spring:
  application:
    name: DPOMBaseMCPServer
  threads:
    virtual:
      enabled: true
  ai:
    mcp:
      server:
        name: DPOMBaseMCPServer
        version: 0.0.1-SNAPSHOT
        type: SYNC
        sse-message-endpoint: /mcp/messages
        sse-endpoint: /sse

server:
  port: 8080

management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState, huaweiCloudCredentials  # 自定义 indicator 在 T03 加，先占位

huaweicloud:
  region: cn-southwest-2          # 贵阳一
  # ak / sk 从环境变量读: HUAWEICLOUD_AK / HUAWEICLOUD_SK

logging:
  level:
    root: INFO
    com.huawei.smartom.agentic: INFO

resilience4j:
  ratelimiter:
    instances:
      ces-readonly:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0
      aom-readonly:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0
      apm-readonly:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0
  retry:
    instances:
      huaweicloud-retryable:
        max-attempts: 3
        wait-duration: 200ms
        exponential-backoff-multiplier: 4
        # 重试条件在 Java 配置里指定 (基于 ErrorCode)
```

### HuaweiCloudProperties

```java
@ConfigurationProperties(prefix = "huaweicloud")
public class HuaweiCloudProperties {
    private String region;
    private String ak;   // from env HUAWEICLOUD_AK
    private String sk;   // from env HUAWEICLOUD_SK
    // 完整 getter/setter
}
```

`application.yml` 里：
```yaml
huaweicloud:
  ak: ${HUAWEICLOUD_AK:}
  sk: ${HUAWEICLOUD_SK:}
```

### HelloWorldTool（用于验证 MCP 工作）

用 Spring AI 的 `@Tool` 注解暴露：

```java
@Component
public class HelloWorldTool {
    @Tool(description = "Test tool. Returns a greeting. Used to verify MCP server is working.")
    public String helloWorld(@ToolParam(description = "Name to greet") String name) {
        return "Hello, " + name + "!";
    }
}
```

并在 `McpServerConfig` 中注册：

```java
@Bean
public ToolCallbackProvider toolCallbackProvider(HelloWorldTool helloWorldTool) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(helloWorldTool)
            .build();
}
```

### Logback

`logback-spring.xml`：用 `net.logstash.logback.encoder.LogstashEncoder` 输出 JSON 到 stdout。MDC 字段：`traceId`, `upstreamTraceId`, `tool`。

依赖：`net.logstash.logback:logstash-logback-encoder:7.4`

### Checkstyle

将团队提供的 IntelliJ 格式转为 Checkstyle 规则文件。关键规则：
- 行宽 120
- 缩进 4 空格
- if/for/while 必须带 `{}`
- 不允许 import *
- import 分组顺序符合 CLAUDE.md §3.1

Maven 插件配置：`mvn validate` 自动跑，失败则 build 失败。

## 验收标准

- [ ] `mvn clean install` 成功
- [ ] `mvn checkstyle:check` 0 violations
- [ ] `mvn spring-boot:run -pl agentic-mcp` 启动成功
- [ ] 启动日志可见 JSON 格式输出
- [ ] `curl http://localhost:8081/actuator/health` 返回 UP
- [ ] `curl http://localhost:8081/actuator/prometheus` 有数据
- [ ] 用 MCP Inspector (`npx @modelcontextprotocol/inspector`) 连接 `http://localhost:8080/sse`，看到 `hello_world` tool，调用返回 `"Hello, World!"`
- [ ] 所有六个模块 build 都成功
- [ ] 测试类 `DpomBaseMcpServerApplicationTests` 跑通

## AI 易错点提醒

1. **Maven artifactId 与 Java 包名的映射**：artifactId 用 `agentic-mcp` 这种短名，Java 包名是 `com.huawei.smartom.agentic.mcp`。父 pom 的 `<modules>` 写**目录名**（即 artifactId）：`agentic-common` / `agentic-adapter-ces` / ... 不要写成 Java 包路径。
2. **Spring AI MCP starter 的依赖名称**确认是 `spring-ai-starter-mcp-server-webmvc`（不是 `spring-ai-mcp-server-spring-boot-starter` 旧名）。
3. **SSE endpoint 路径**：默认 `/sse`，消息路径默认 `/mcp/messages`，按需调整。
4. **`@SpringBootApplication` 的 scanBasePackages**：必须显式指定，因为启动类在 `com.huawei.smartom.agentic.mcp`，默认只扫这个包及子包，扫不到其他模块的 `com.huawei.smartom.agentic.adapter.*`。
5. **logback-spring.xml** 必须用 `-spring.xml` 后缀，否则 Spring profile 占位符不生效。
6. **不要在这个任务里引入任何华为云 SDK 依赖**——那是 T04 的事。
7. **不要写 Lombok**——见 ADR-003。

## 完成后

打一个 commit，提 PR：`feat(T01): project skeleton with hello-world tool`。
