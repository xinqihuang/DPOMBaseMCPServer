# ADR-005: DPOMBase evidence-only 服务边界

- 状态: Accepted（替代 2026-08-21 的 Phase 1B Investigation SoR 决策）
- 日期: 2026-08-27
- 权威决策: `D:\code\ADR.md`

## Context

DPOMAgent 已被确定为 Investigation/Diagnosis 的唯一权威来源。让 DPOMBase 同时保存调查状态、生成报告并发布
Kafka 诊断事件，会产生双权威、契约漂移和部署耦合。

## Decision

DPOMBaseMCPServer 仅提供 AOM/APM/CES/LTS/CCE/CMDB/OBS 的证据采集、标准化、确定性聚合与受控 Artifact
能力。它不承载模型、诊断状态、ToolUse 决策、报告、诊断消息或生产资源变更。

DPOMAgent 负责 Investigation、Diagnosis、报告和发往 SRE Intelligence 的 Diagnosis Event；
HuaweiCloudAlarmChangeGuard 负责 CES/AOM/APM 告警规则等生产变更。

仓库移除 diagnosis、persistence、messaging 模块及对兄弟 `contracts` 目录的构建依赖。OBS Put/Head/Get 是
证据 Artifact 操作，不视为业务资源变更，目标仍必须由部署配置和最小权限 IAM 约束。

## Consequences

- DPOMBase 可独立构建和部署，不需要 Kafka、MySQL、模型凭证或同级契约仓库；
- MCP 暴露面由 evidence-only allowlist 约束；
- 生产告警变更不再能通过 DPOMBase 调用；
- 旧 Phase 1B Investigation SoR、Kafka publication 与 Portal progress 设计只保留在归档历史中，不再生效。

## References

- 工作区 ADR：`D:\code\ADR.md`
- OpenSpec change：`openspec/changes/remove-diagnosis-from-dpom-base`
