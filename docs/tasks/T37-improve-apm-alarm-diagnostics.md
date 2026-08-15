# T37 — 修复 APM 告警诊断工具契约

## 背景

真实只读验证确认：`list_apm_alarm_data` 仅设置 `x-business-id` 时，上游返回
`invalid parameter`；同时设置请求头和 body `business_id` 后可正常返回告警。
当前工具把二者暴露为两个独立参数，容易导致 Agent 重复试错。

## 范围

- `business_id` 必须来自 `list_apm_business`。
- 未显式提供 `business_id_filter` 时，自动复用有效 `business_id`。
- 明确区分服务端 APM SDK endpoint region 与请求中的资源 region。
- 上游 HTTP 4xx 参数错误必须不可重试，不得映射为可重试的 5xx 错误。
- 保持工具只读，不增加任何生产写操作。

## 非范围

- 不修改 APM Trace 实现。
- 不修改华为云资源、告警规则或 JVM 参数。
- 不处理仓库外临时 MCP 客户端的 SSE 进程退出逻辑。

## 验收标准

- 只传发现得到的 `business_id` 时，SDK 请求的 header 与 body 均获得同一 id。
- 显式 `business_id_filter` 仍可覆盖 body 过滤值。
- HTTP 400 映射为不可重试错误。
- 相关单元测试及 `mvn clean verify` 通过。
- 使用真实只读凭据验证 `dpframework`（111092）可拉取 `cn-north-9` 活动告警，且不记录凭据。
