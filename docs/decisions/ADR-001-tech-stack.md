# ADR-001: 整体技术选型

- 状态: Accepted
- 日期: 2026-05-21

## Context

DPOMBaseMCPServer 需要把华为云 AOM/APM/CES 监控能力封装为 MCP Server，供智能运维 Agent 使用。需要选定语言、框架、构建工具、MCP 实现。

## Decision

- **JDK 21** —— LTS，虚拟线程对本项目（IO 密集 + 同步阻塞 SDK）天然契合
- **Spring Boot 3.4.x** —— 团队基线，CCE 部署成熟
- **Spring AI MCP Server 1.0.4** (`spring-ai-starter-mcp-server-webmvc`) —— 当前 1.0 稳定流，避开 2.0 milestone 的 breaking changes
- **WebMvc + SSE**（非 WebFlux）—— 虚拟线程能扛 IO 并发，省去 reactive 编程复杂度
- **Maven** —— SDK 官方示例全用 Maven，AI 写 `pom.xml` 稳定
- **华为云 Java SDK v3.1.196** —— 当前最新稳定版

## Consequences

- ✅ 团队 Spring Boot 经验直接复用
- ✅ AI 生成 Spring Boot 代码训练数据最丰富
- ✅ 虚拟线程让我们不必引入 reactive
- ⚠️ Spring Boot 包体较大，但 CCE 部署不敏感
- ⚠️ Spring AI 1.0.4 之后会有 2.0 breaking change，未来升级要审慎

## Alternatives Considered

- **Spring AI 2.0.0-M3**：milestone，含 MCP 注解包重命名 + Jackson 3 迁移，企业生产不上
- **官方 MCP Java SDK 裸用**：要自己搭 SSE，没必要
- **Quarkus MCP**：启动快，但生态小、AI 训练数据少
- **WebFlux**：本项目无 reactive 必要，且华为云 SDK 同步阻塞，要包 `Mono.fromCallable`
