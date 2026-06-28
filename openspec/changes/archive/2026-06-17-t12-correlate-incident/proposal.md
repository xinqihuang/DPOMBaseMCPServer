## Why

智能运维 Agent 在事故分析时，常需在同一时间窗内交叉查看基础设施告警（CES）、应用 / 节点日志（AOM）、请求 trace 与拓扑（APM）四类证据。若由 Agent 自己串行编排多个单组件 tool，会带来多轮调用延迟、错误处理分散、上下文膨胀等问题。本变更提供一个编排型 MCP 工具 `correlate_incident`，在一次调用内并发扇出四个分支，每个分支独立返回 `success` / `failure` / `skipped` 三态。

> 注：本变更为**存量回填——已于早期 commit 交付**（提交 `4c346d6`，2026-05-28 落地），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

## What Changes

- 新增 MCP 只读编排工具 `correlate_incident`，接收一个时间窗 `(startTimeMillis, endTimeMillis)` 与一组可选过滤条件，并发扇出四个分支：`ces_alarms` / `aom_logs` / `apm_traces` / `apm_topology`。
- 工具**不直连华为云 SDK**，而是组合 monitoring 层已有的 `CesAlarmService` / `AomLogService` / `ApmTraceService` 能力。
- 采用 JDK 21 虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`）承载分支扇出，单分支失败 / 跳过不阻塞其他分支；每分支以三态 `CorrelateBranchResult`（`skipped` / `data` / `error`）独立返回。
- 复用下游 service 各自的限流域（`ces-readonly` / `aom-readonly` / `apm-readonly`）与重试 / 超时策略，本层不重复包裹。

## Capabilities

### New Capabilities

- `correlate-incident`: 在单次调用内，于同一时间窗并发拉取 CES 告警 / AOM 日志 / APM trace / APM 拓扑四个分支的证据，各分支以 `success` / `failure` / `skipped` 三态独立返回，供 Agent 构建跨组件证据链。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-monitoring`（新增 `correlate` 包：`CorrelateIncidentRequest` / `CorrelateIncidentResponse` / `CorrelateBranchResult` / `CorrelateError` 四个 DTO record + `CorrelateIncidentService` 编排服务）、`agentic-mcp`（`CorrelateIncidentTool` + `McpServerConfig` 注册到 `ToolCallbackProvider`）。
- 配置：无新增配置项；限流 / 重试 / 超时由下游三个 service 透传（一次调用跨三个限流域，扣 4 个配额）。
- 不涉及写操作；不改动既有 `CesAlarmService` / `AomLogService` / `ApmTraceService` 的对外契约。
