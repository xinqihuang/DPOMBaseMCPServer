# DPOMBaseMCPServer

DPOMBaseMCPServer 是面向 DPOMAgent 的证据工具服务。它只负责从华为云监控与日志系统采集、标准化、
关联和存取有界证据；DPOMAgent 是 Investigation、Diagnosis、ToolUse 决策和诊断结论的唯一权威来源。

## 服务边界

本服务允许：

- 查询 CES、AOM、APM、LTS 等观测数据和发现信息；
- 对多个只读来源做确定性的证据聚合，不生成假设或根因；
- 将证据包写入可配置的 OBS 位置，并执行 Head/Get 与完整性校验。

本服务禁止：

- 承载大模型、Prompt、Agent 或 ToolUse 决策；
- 保存 Incident/Investigation/Run/Step/Hypothesis/Conclusion 状态；
- 生成诊断报告、Diagnosis Event 或 Diagnosis Progress；
- 依赖 Kafka、诊断 Outbox、外部兄弟 `contracts` 目录；
- 修改 CES/AOM/APM 告警规则或其他生产资源。生产变更由 `HuaweiCloudAlarmChangeGuard` 管理。

## Maven 模块

```text
agentic-mcp -> agentic-monitoring -> agentic-adapter-* -> agentic-common
```

- `agentic-common`：统一错误、配置、弹性调用和通用类型。
- `agentic-adapter`：CES/AOM/APM/LTS/OBS SDK 的隔离与稳定 DTO。
- `agentic-monitoring`：参数校验、证据查询、确定性聚合与 OBS Artifact 处理。
- `agentic-mcp`：唯一可执行模块和 MCP Tool 暴露面。

仓库不再包含 `agentic-diagnosis`、`agentic-persistence` 或 `agentic-messaging`。

## 构建验证

要求 JDK 21 与 Maven 3.9+：

```shell
mvn clean verify
```

构建不需要同级 `contracts` 仓库、Kafka、MySQL 或模型凭证。真实云端集成测试默认关闭，单元测试和
录制契约测试不读取真实 AK/SK。

## 配置

华为云凭据只通过部署环境注入：`HUAWEICLOUD_AK`、`HUAWEICLOUD_SK`。OBS 目标必须按环境配置，
包括 `DPOM_OBS_ENDPOINT`、`DPOM_OBS_BUCKET`、`DPOM_OBS_PREFIX` 和 `DPOM_OBS_SERVICE_CODE`；代码中没有测试桶默认值。

详细约束见 [架构文档](./docs/architecture.md) 和
[OpenSpec 变更](./openspec/changes/remove-diagnosis-from-dpom-base/)。

## License

Internal use — Huawei SmartOM.
