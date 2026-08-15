# T32 — 收紧只读运行时边界

## 目标

默认及生产运行时不注册 CES 通知屏蔽写工具。只有隔离的人工运维进程同时启用专用 profile 和显式配置时，历史写工具才可发现。

## 验收

- 默认 `tools/list` 不含 `create_notification_mask`、`delete_notification_masks`。
- 只启用任一开关仍不注册。
- 同时启用两个开关时才注册。
- DPOMAgent 的边界文档明确禁止启用和调用写工具。
- `mvn clean verify` 与 OpenSpec strict validate 通过。
