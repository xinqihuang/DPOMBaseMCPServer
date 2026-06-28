## Context

存量工具回填。原始 spec：`docs/specs/tools/query_lts_log_context.md`（v1.0, Approved）；原始任务卡：`docs/tasks/T18-query-lts-log-context.md`（状态 Done）。本工具是 `query_lts_logs` 的后置发现链工具，依赖 T16 已就绪的 `LtsLogAdapter.listLogContext` 与对应 DTO，沿用 T17 的 service + tool 风格。本文承载 OpenSpec 主 spec 放不下的重契约：SDK 类/方法/版本、字段映射、错误码、非功能要求、不一致的时间/游标参数格式、AI 易错点。

## Goals / Non-Goals

**Goals:**
- 给定 `log_group_id` + `log_stream_id` + 目标日志 `line_num` + `cursor_time`，无损暴露 LTS `listLogContext` 的前 N + 后 N 条上下文拉取能力。
- 支持 scroll 模式分页（续翻页时仅传 `scroll_id`）。
- 与 `query_lts_logs` 形成可衔接的发现链：先 search 命中，再 context 还原。

**Non-Goals:**
- 不接受时间区间过滤（语义已由 cursor 锁定）。
- 不接受 keywords / query / labels / SQL（不是 search 类工具）。
- 不做客户端排序（SDK 已按上下文时序返回）。
- 不做跨流上下文（一次只支持一个 stream）。
- 不新增 RateLimiter、健康检查、自动 pre-fetch（让 `query_lts_logs` 直接附带上下文属另一类工具设计）。

## Decisions

### SDK 映射

- **SDK 类**：`com.huaweicloud.sdk.lts.v2.LtsClient`
- **SDK 方法**：`listLogContext(ListLogContextRequest)`
- **SDK 版本**：3.1.177（项目共用 `${huaweicloud-sdk.version}`；字段缺失先怀疑版本）
- **HTTP**：`POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/context`
- **SDK 类型不泄漏**：service / mcp 层不得 import `com.huaweicloud.sdk.lts.*`，SDK ↔ DTO 映射全在 T16 adapter 内完成。

**入参 → SDK 字段映射**：

| MCP 输入 | SDK 字段 | 备注 |
|---|---|---|
| `log_group_id` | path `log_group_id` | 必填 |
| `log_stream_id` | path `log_stream_id` | 必填 |
| `line_num` | body `line_num` | String |
| `cursor_time` | body `__time__`（SDK Java 字段名 `time`） | String，注意 SDK 字段名与对外名不一致 |
| `backwards_size` | body `backwards_size` | Integer，SDK 默认 100 |
| `forwards_size` | body `forwards_size` | Integer，SDK 默认 100 |
| `scroll_id` | body `scroll_id` | String |

**响应字段（`LtsListLogContextResponse`，T16 已映射）**：`logs[]`（每项 `content` / `line_num` / `labels`）、`total_count`、`backwards_count`、`forwards_count`、`is_query_complete`。

### 不一致的时间 / 游标参数格式

- `cursor_time` 是**目标日志的 `__time__` 字符串**（上游 String，对应 SDK Java 字段 `time`，非 `OffsetDateTime`、非 UTC 毫秒），原样透传，不做本地解析/格式转换。注意它在不同 LTS 接口中语义不同：本工具是"以这一条为中心"的游标，而非时间区间端点；不要与 `query_lts_logs` 的 `start_time`/`end_time`（区间，毫秒）混用。
- `line_num` 是字符串纳秒级序号（如 `1717400000000000000`），非整型，禁止本地比较/运算，只做透传。
- **scroll_id 游标当前 SDK 响应未暴露为单独字段**：`is_query_complete=false` 表示上游仍可翻页，但 SDK 不返回独立 `scroll_id`。本期 Agent 翻页直接复用上一次响应**尾行**的 `line_num` + `cursor_time` 作为新目标点继续调用，**不要**为补 `scroll_id` 输出字段去改 T16 DTO。

### 校验规则（service 层，spec §3.2）

1. `log_group_id` / `log_stream_id` 必填且非空白 → 否则 `INVALID_PARAM`。
2. 模式互斥，必须满足其一：首次模式（`line_num` 与 `cursor_time` 都非空，`scroll_id` 任意）或翻页模式（`scroll_id` 非空，`line_num`/`cursor_time` 任意）；都不满足 → `INVALID_PARAM`，提示 "either (line_num + cursor_time) or scroll_id is required"。
3. `line_num` 与 `cursor_time` 不能只给一个（除非走 scroll 模式且两者都不给）→ 否则 `INVALID_PARAM`，提示 "line_num and cursor_time must be provided together"。
4. `backwards_size` / `forwards_size` 非 null 时取值 `[0, 500]` → 否则 `INVALID_PARAM`。
5. `backwards_size` 与 `forwards_size` 不能同时为 0 → 否则 `INVALID_PARAM`，提示 "at least one of backwards_size / forwards_size must be > 0"。

### 错误码映射

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败 | INVALID_PARAM | false |
| HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

失败响应携带 `error_code` / `error_message` / `upstream_trace_id`（华为云 `X-Request-Id`，可空）/ `retryable`。

### 非功能要求

- **限流**：复用 `lts-readonly` RateLimiter（10 QPS），与 `query_lts_logs` 共享配额；不新增 `application.yml` 条目。
- **重试**：仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避。
- **超时**：SDK 传输层 10s。
- **可观测**：Micrometer `mcp_tool_invocation{tool="query_lts_log_context", result="success|error", error_code="..."}`；INFO 日志含 `logGroupId` / `logStreamId` / `lineNum` / `backwardsSize` / `forwardsSize` / 是否走 scroll 模式 / 耗时 / `upstream_trace_id`。

## Risks / Trade-offs

- **AI 易错点**：
  1. Tool 参数与 DTO record 顺序对齐——`LtsListLogContextRequest` 7 字段按位置构造，错一个即全错。
  2. scroll 模式下 `line_num`/`cursor_time` 可同时为空、不可只给一个；不要把首次模式的必填规则照搬到 scroll 模式。
  3. `backwards_size`/`forwards_size` 区分 null（未传，走 SDK 默认 100）与 0（显式禁用）；同时为 0 拒绝。
  4. Tool description 必须提示 "use this after query_lts_logs"，否则 LLM 可能误用本工具做关键字搜索（本工具不接受 keywords/query/SQL）。
  5. `cursor_time` 对外名与 SDK Java 字段名 `time` 不一致；映射在 adapter 内，service/mcp 层不感知。
- **遗留**：`scroll_id` 未作为独立输出字段（SDK 限制，翻页复用尾行 line_num+cursor_time）；冒烟脚本、Micrometer 看板、README 示例本期未交付。
- **MCP annotations**：`readOnlyHint=true` / `destructiveHint=false` / `idempotentHint=true` 在 Spring AI 1.0.4 `@Tool` 未实际透出，当前仅为语义意图。
