## Why

存量回填，已于早期 commit 交付（原任务卡 `docs/tasks/T19-lossless-dto-remediation.md`，状态 Ready→Done，按 PR-0~PR-5 拆分逐步合入），此处补齐 OpenSpec 规格以纳入 spec-driven 管理。

仓库里**所有 response DTO 系统性偏薄、丢字段**，不止 alarm history。两个叠加根因：

1. **约定被误读**：`CLAUDE.md §4.1`「自定义 DTO 包裹 SDK」本意是「稳定契约 + 不泄漏 SDK 类型」，但理由写成「字段命名长、嵌套深」，被 AI 读成「做最小子集」。多个 DTO 的 Javadoc 明文写了「最小信息集」，对诊断 Agent 最关键的信号（触发值、阈值规则、维度、record_id、各时间戳、mask_status 等）被系统性砍掉。
2. **打错 API 版本**：alarm history 链路用 CES **v1**（`AlarmHistoryInfoResp`），但目标字段只存在于 **v2**（`AlarmHistoryItemV2`）。

本变更属**基础设施 / 横切（infra）**层：它是对既有跨服务 adapter 层 DTO 的系统性治理 + 一处 API 版本切换 + 契约测试兜底网，**不引入任何对外的新工具能力，也不改任何 `@Tool(name=...)` 契约名**，因此**不引入新的 capability spec、也不修改既有 capability spec**。各工具的对外契约（参数、行为）保持不变，只在响应里**只增不改名**地补齐字段。

## What Changes

- 改 `CLAUDE.md §4.1`：把「最小子集」语义改成「无损投影 + 钉死 API 版本 + SDK 源码为权威 + 契约测试兜底」（替换文本见任务卡，照贴）。
- **CES alarm history 切 v2 + 无损化（样板）**：链路从 CES v1 `listAlarmHistories`（`AlarmHistoryInfoResp`）切到 v2 `listAlarmHistories`（`AlarmHistoryItemV2`），`CesAlarmHistory` 无损覆盖 v2 全字段含嵌套子 record；修 `CorrelateIncidentService` 对该 DTO 的引用。
- **全 response DTO 审计 + 无损化**：对审计表中每一个 response DTO，sparse-checkout 其对应 SDK 源码 model 类逐字段比对，确认有损的全部补齐（拆嵌套子 record、补字段、改类型），本就无损的标注 OK 不动；findings 沉淀到 `docs/audit/dto-losslessness-audit.md`。覆盖 CES（metrics / metric-data / batch / notification-mask）、APM（span / topology / traces）、AOM（logs / metrics / sample）、LTS（logs / log-context）。
- **每个被改 DTO 配契约测试**：真实 SDK 响应样本落 `test/resources/sdk-samples/<svc>/`，反序列化经 adapter 映射，断言 DTO 覆盖样本全字段，漂移即 fail。
- **约束（防蔓延）**：不改 request DTO 设计、不改任何 `@Tool(name=...)` 契约名、不加新 tool、不动分页 / 限流 / 错误码语义；不改已有响应字段名（只增不改名，保证 Agent 向后兼容）；本就无损的 DTO 只标注不重构。

## Capabilities

### New Capabilities

- 无（基础设施变更）。本变更不对外暴露任何新 MCP 工具能力，只对既有 adapter 层响应 DTO 做无损投影治理 + 一处 SDK API 版本切换 + 契约测试兜底网。

### Modified Capabilities

- 无。各工具的对外契约名与行为不变，响应字段「只增不改名」属向后兼容补全，不构成 capability spec 的契约变更。

## Impact

- 文档：替换 `CLAUDE.md §4.1`（根因修订，PR-0 先合）；新增 / 累加 `docs/audit/dto-losslessness-audit.md`（审计表中每个 DTO 的 findings 结论）。
- 模块（adapter 层）：
  - `agentic-adapter-ces`：`CesAlarmHistory` / `CesListAlarmsResponse` 切 v2 重画并无损化；`CesListMetricsResponse` / `CesQueryMetricDataResponse` / `CesBatchQueryMetricDataResponse` / `CesListNotificationMasksResponse` 审计补齐。
  - `agentic-adapter-apm`：`ApmSpan` / `ApmQueryTracesResponse` / 拓扑相关 DTO 审计补齐。
  - `agentic-adapter-aom`：`AomQueryLogsResponse` / `AomListMetricsResponse` / `AomSampleSeries` 等审计补齐。
  - `agentic-adapter-lts`：`LtsListLogsResponse` / `LtsLogEntry` / `LtsListLogContextResponse` 审计补齐。
- 引用方：`agentic-monitoring` 的 `CorrelateIncidentService` 随 alarm history 切 v2 调整对 `CesAlarmHistory` 的字段引用，编译通过、行为不回归。
- 配置：无新增配置项；不动分页 / 限流（各 `*-readonly` RateLimiter 不变）/ 重试 / 错误码语义。
- 依赖方向：遵循 `mcp → monitoring → adapter → common`；SDK 类型仍只在 adapter 内部，不外泄到 monitoring / mcp。
- 兼容性：响应 DTO 只增字段、不改既有字段名，对 Agent 向后兼容；唯一行为变化是 alarm history 数据源从 CES v1 切到 v2（字段更全）。
- 不涉及写操作；不改 request DTO / 工具名 / 新增工具。
