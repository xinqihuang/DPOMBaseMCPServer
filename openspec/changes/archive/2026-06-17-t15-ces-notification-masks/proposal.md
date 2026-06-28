## Why

存量回填，已于早期 commit 交付（commit `7bf5907`，2026-05-28 完成）。此处补齐 OpenSpec 规格，将 CES 告警通知屏蔽三件套纳入 spec-driven 管理。

智能运维 Agent 在变更窗口 / 维护期需要完整的 CES 告警屏蔽生命周期管理：变更前临时屏蔽受影响告警，变更后解除屏蔽，并在审计 / 删除前定位目标屏蔽规则。三个工具（`create` / `delete` / `list`）在同一 commit 中一起交付，共享 v2 SDK client 与 service 编排层，故合为一张任务卡 / 一个 change。本变更也是项目首次引入：CES v2 SDK（独立于既有 v1 client）、写操作 tool、以及 `destructiveHint=true` 的破坏性 tool。

## What Changes

- 新增 MCP 写工具 `create_notification_mask`，封装 CES v2 SDK `BatchUpdateNotificationMasks`，创建一条告警通知屏蔽规则，支持五种 `relation_type` 与三种 `mask_type`。
- 新增 MCP 写工具 `delete_notification_masks`（`destructiveHint=true`），封装 CES v2 SDK `BatchDeleteNotificationMasks`，按 ID 批量删除（一次最多 100 条），幂等。
- 新增 MCP 只读工具 `list_notification_masks`，封装 CES v2 SDK `ListNotificationMasks`，分页 + 多条件过滤查询屏蔽规则。
- 新增 CES v2 SDK client bean（`CesV2ClientConfig`），与 v1 `cesClient` 共存；新增 `ces-write` 限流域（5 QPS），`list` 复用 `ces-readonly`（10 QPS）。

## Capabilities

### New Capabilities

- `create-notification-mask`: 创建一条 CES 告警通知屏蔽规则，供变更窗口前临时屏蔽告警通知。
- `delete-notification-masks`: 按 ID 批量删除 CES 告警通知屏蔽规则，供变更结束后解除屏蔽，破坏性 + 幂等。
- `list-notification-masks`: 分页 + 多条件过滤查询 CES 告警通知屏蔽规则，供审计与删除前定位目标 ID。

### Modified Capabilities

<!-- 无 -->

## Impact

- 模块：`agentic-adapter-ces`（新增 `CesV2ClientConfig`、`CesNotificationMaskAdapter` + `Impl`、10 个 DTO record）、`agentic-monitoring`（`CesNotificationMaskService` 统一编排三个方法）、`agentic-mcp`（三个 `@Tool` + `McpServerConfig` 注册）。
- 配置：`application.yml` 新增 `ces-write` RateLimiter（5 QPS）；`list` 复用既有 `ces-readonly`（10 QPS）。
- 引入 CES v2 SDK（v3.1.177），与 v1 client 共存，bean 命名 `cesV2Client` 以区分。
- 含项目首个写操作与首个 `destructiveHint=true` 破坏性工具。
