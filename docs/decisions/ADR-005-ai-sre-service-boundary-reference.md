# ADR-005: AI For SRE Phase 1 服务边界引用

- 状态: Accepted
- 日期: 2026-08-21
- 权威决策: `D:\code\ADR.md`

## Context

工作区 Phase 1 目标只有三个后端部署单元。本文记录 DPOMBaseMCPServer 从 Phase 1A 只读证据网关
演进为 Phase 1B 在线诊断系统记录的责任。

## Decision

DPOMBaseMCPServer 的 provider adapter 继续保持生产侧只读证据边界，同时本服务新增：

- 封装华为云 APM、CES、AOM、LTS、CCE、CMDB 等只读访问与供应商 DTO；
- 保留既有审批约束下的受控 OBS 证据传输；
- Incident、Investigation、Run、Step、Observation、Hypothesis、Conclusion、预算、checkpoint 和审计；
- 默认关闭、可恢复的 Diagnosis Orchestrator 与 source authority epoch；
- 事务 publication intent、Diagnosis Event v2、Progress v1、Kafka 和 Portal REST/SSE；
- 不承载 Dataset、Judge 聚合、Release Gate 或生产写工具，不与其他服务共享数据库。

Phase 1B change `complete-phase1-three-service-convergence` 负责兼容、切换、回滚与安全隔离。切换不复制
DPOMAgent 历史行，而是按 authority epoch 停止新调查、drain 旧运行、验证后切换新来源。

## Consequences

- 本仓库现有只读 provider 工具和安全边界保持不变；新 runtime 不扩大生产写工具面。
- SRE Intelligence 与 DeepEval 不直接访问华为云 SDK 或本服务数据库。
- 后续诊断事件契约由中立工作区资产定义，而不是由本仓库 Java DTO 单方面定义。

## References

- 工作区 ADR：`D:\code\ADR.md`
- OpenSpec change：`D:\code\openspec\changes\complete-phase1-three-service-convergence`
- 中立契约目录：`D:\code\contracts\diagnosis-event\v1`
