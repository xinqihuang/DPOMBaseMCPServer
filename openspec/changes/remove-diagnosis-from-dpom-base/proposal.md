## Why

DPOMBaseMCPServer 当前误包含 Investigation、Hypothesis、Conclusion、诊断报告和 Kafka Diagnosis Event 发布代码，
与其“供 DPOMAgent 调用的证据采集 MCP Server”定位冲突，并造成 Maven 测试依赖仓库外 `../contracts`。现在需要
彻底移除诊断职责，使服务能够独立 clone、独立构建并保持无状态证据边界。

## What Changes

- **BREAKING**：删除 `agentic-diagnosis`、`agentic-persistence` 和 `agentic-messaging` Maven 模块及全部 Investigation、诊断报告、
  Diagnosis Event/Progress 构造、Kafka 发布、Outbox/Replay 职责。
- 从 persistence、MCP composition、配置、迁移和测试中删除诊断状态与消息发布依赖，仅保留证据、工具审计、
  OBS 等工具侧必要元数据。
- 删除 `${maven.multiModuleProjectDirectory}/../contracts`、父目录 contracts 扫描和 DPOMBase Producer conformance
  测试；Diagnosis Event/Progress conformance 归 DPOMAgent。
- 删除 `diagnose_trace` 根因线索工具以及 CES/APM 告警规则写适配器；生产变更统一归 HuaweiCloudAlarmChangeGuard。
- Evidence Manifest 等 DPOMBase 真正消费的契约改为仓库内固定测试资源或版本化测试依赖。
- 增加架构守卫，禁止 LLM、ToolUse 决策、RCA、Investigation 状态、诊断报告和 Kafka Diagnosis Event 发布重新进入。
- 保持 APM/AOM/CES/LTS/CCE、CMDB、OBS 和现有 MCP 证据工具的外部行为兼容。

## Capabilities

### New Capabilities

- `evidence-only-service-boundary`: 定义 DPOMBaseMCPServer 只采集、标准化和返回可追溯证据，并禁止任何诊断职责。

### Modified Capabilities

无。现有单工具 capability 的查询契约保持不变。

## Impact

- 删除模块：`agentic-diagnosis`、`agentic-persistence`、`agentic-messaging`。
- 调整模块：根 POM、`agentic-common`、`agentic-monitoring`、`agentic-mcp` 及 composition 配置。
- 删除 Kafka 与 Diagnosis Event/Progress 相关依赖、配置、数据库迁移、指标和测试。
- DPOMAgent 成为唯一 Investigation/Diagnosis 权威与 Diagnosis Event Producer。
- Maven 验收必须在没有兄弟 `contracts` 目录的干净 clone 中通过。
