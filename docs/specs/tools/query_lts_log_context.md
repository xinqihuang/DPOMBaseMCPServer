# Spec: query_lts_log_context

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 在 {@code query_lts_logs} 命中一条目标日志后，拉取它前后 N 条上下文，
还原事故发生时该日志流的真实时序与因果链。

典型场景:
- Agent 找到一条 ERROR 日志，要看它发生前的初始化流程 / 发生后的后续告警
- Agent 在 SQL 分析模式下定位到某关键 line_num，要回到原始日志流看上下文
- Agent 拿到的目标日志中文本被截断，要看完整序列

定位：**`query_lts_logs` 的后置工具**——必须先用 `query_lts_logs` 得到目标日志的
`line_num` 与 `cursor_time`，再调本工具。本工具不接受时间区间 / 关键字 / SQL，因为它的语义
就是"以这一条为中心向前后扩展"。

## 2. 范围边界

**做**:
- 给定 `log_group_id` + `log_stream_id` + 目标日志的 `line_num` + `cursor_time`，
  拉取前 N（backwardsSize）+ 后 N（forwardsSize）条日志
- 支持 scroll 模式分页（连续翻页时只传 `scroll_id`）
- 入参做 service 层校验（必填项 / backwards/forwards 范围 / cursor 与 scroll 互斥）
- 上游异常映射到 `ErrorCode`，trace id 透传

**不做**:
- 不接受时间区间过滤（语义已经由 cursor 锁定）
- 不接受 keywords / query / labels（不是 search 类工具）
- 不做客户端结果排序——SDK 已按上下文时序返回
- 不做跨流上下文（一次只支持一个 stream）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `query_lts_log_context`
- description（Agent 看到的，决定是否调用）:

  > Fetch surrounding log entries for a specific target log line in a Huawei Cloud
  > LTS log stream. Use this after query_lts_logs identifies a line of interest —
  > pass the target's line_num and cursor_time (from the previous response) plus
  > backwards_size / forwards_size to retrieve N entries before and after it.
  > Useful for reconstructing event sequence around an incident. For follow-up
  > pages from a single first call, pass only scroll_id from the previous response.

- annotations:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `log_group_id` | string | 是 | — | LTS 日志组 id |
| `log_stream_id` | string | 是 | — | LTS 日志流 id |
| `line_num` | string | 条件必填 | — | 目标日志的行号；首次调用必填，与 `cursor_time` 成对 |
| `cursor_time` | string | 条件必填 | — | 目标日志的 `__time__`；首次调用必填，与 `line_num` 成对 |
| `backwards_size` | int | 否 | null | 向前取多少条；取值 [0, 500]，SDK 默认 100 |
| `forwards_size` | int | 否 | null | 向后取多少条；取值 [0, 500]，SDK 默认 100 |
| `scroll_id` | string | 否 | null | scroll 续翻页 id；给了 `scroll_id` 时可省略 `line_num`/`cursor_time` |

**输入校验规则**（service 层执行）:
- `log_group_id` / `log_stream_id` 必填且非空白 → 否则 `INVALID_PARAM`
- 模式互斥：必须满足以下二者之一
  - 首次模式：`line_num` 与 `cursor_time` 都非空（`scroll_id` 任意）
  - 翻页模式：`scroll_id` 非空（`line_num` / `cursor_time` 任意）
  - 都不满足 → `INVALID_PARAM`，提示 "either (line_num + cursor_time) or scroll_id is required"
- `line_num` 与 `cursor_time` 不能只给一个（除非走 scroll 模式且两者都不给）
- `backwards_size` 非 null 时取值 `[0, 500]` → 否则 `INVALID_PARAM`
- `forwards_size` 非 null 时取值 `[0, 500]` → 否则 `INVALID_PARAM`
- `backwards_size` 与 `forwards_size` 不能同时为 0（否则上游会返回空数组，体验差）
  - 注：本规则在 service 层拦下；如果用户希望主动拿空集，可显式跳过此校验（暂不暴露此开关）

### 3.3 输出契约（成功）

```json
{
  "logs": [
    {
      "content": "2026-06-03T10:15:21Z INFO  [order-svc] starting transaction ...",
      "line_num": "1717400000000000000",
      "labels": {
        "host_name": "ecs-i-abc",
        "container_name": "order-svc"
      }
    },
    {
      "content": "2026-06-03T10:15:23Z ERROR [order-svc] failed to ...",
      "line_num": "1717400000000000001",
      "labels": { "...": "..." }
    }
  ],
  "total_count": 2,
  "backwards_count": 1,
  "forwards_count": 1,
  "is_query_complete": true
}
```

字段说明：
- `logs` 按时序排列，先前后后；目标日志本身会包含在序列中
- `backwards_count` / `forwards_count`：实际返回的前 / 后向条数，可能小于请求的 size
  （边界情况：日志流头部 / 尾部）
- `is_query_complete=false` 表示上游仍可翻页，下一次调用应使用本响应里的 scroll 凭据
  （注：当前 SDK 响应未把 `scroll_id` 暴露为单独字段；翻页时 Agent 直接复用上一次响应里
  最尾行的 `line_num` + `cursor_time` 作为新的目标点继续调用即可）

### 3.4 输出契约（失败）

```json
{
  "error_code": "INVALID_PARAM | UPSTREAM_THROTTLED | UPSTREAM_AUTH_FAILED | UPSTREAM_ERROR | TIMEOUT | INTERNAL",
  "error_message": "human readable",
  "upstream_trace_id": "华为云返回的 X-Request-Id",
  "retryable": true | false
}
```

错误码映射:

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败 | INVALID_PARAM | false |
| HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.lts.v2.LtsClient`
- **SDK 方法**: `listLogContext(ListLogContextRequest)`
- **SDK 版本**: 3.1.177（项目共用 `${huaweicloud-sdk.version}`）
- **HTTP**: `POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/context`

**字段映射**（adapter 层已实现，参见 T16）：

| MCP 输入 | SDK 字段 |
|---|---|
| `log_group_id` | path `log_group_id` |
| `log_stream_id` | path `log_stream_id` |
| `line_num` | body `line_num` |
| `cursor_time` | body `__time__`（SDK Java 字段名 `time`） |
| `backwards_size` | body `backwards_size` |
| `forwards_size` | body `forwards_size` |
| `scroll_id` | body `scroll_id` |

## 5. 非功能要求

- **限流**: 复用 `lts-readonly` RateLimiter（10 QPS），与 `query_lts_logs` 共享配额
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避
- **超时**: SDK 传输层 10s
- **可观测**:
  - Micrometer: `mcp_tool_invocation{tool="query_lts_log_context", result="success|error", error_code="..."}`
  - INFO 日志: `logGroupId` / `logStreamId` / `lineNum` / `backwardsSize` / `forwardsSize` / 是否走 scroll 模式 / 耗时 / `upstream_trace_id`

## 6. 测试策略（DoD）

### 单元测试

`LtsLogContextToolTest`：

| ID | 用例 | 期望 |
|---|---|---|
| UT-T1 | 全合法（首次模式 + sizes） | service 收到字段对齐的 request，返回值原样透传 |
| UT-T2 | service 抛 `InvalidParamException` | ErrorResponse / `INVALID_PARAM` |
| UT-T3 | service 抛 `UpstreamException(429)` | ErrorResponse / `UPSTREAM_THROTTLED` / trace id 透传 |

`LtsLogContextServiceTest`（mock adapter）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-S1 | `request == null` | INVALID_PARAM |
| UT-S2 | `log_group_id` 空白 | INVALID_PARAM |
| UT-S3 | `log_stream_id` 空白 | INVALID_PARAM |
| UT-S4 | 既无 `line_num`+`cursor_time` 也无 `scroll_id` | INVALID_PARAM，提示 "either ... or scroll_id" |
| UT-S5 | 只给 `line_num` 不给 `cursor_time`（无 `scroll_id`） | INVALID_PARAM，提示 "line_num and cursor_time" |
| UT-S6 | `backwards_size = -1` 或 `> 500` | INVALID_PARAM |
| UT-S7 | `backwards_size = 0 && forwards_size = 0` | INVALID_PARAM，提示 "at least one of backwards/forwards must be > 0" |
| UT-S8 | 只走 scroll 模式（line_num/cursor_time 都空） | 委托 adapter，不抛异常 |
| UT-S9 | 全合法（首次模式） | 委托 adapter，返回 adapter 响应 |

### 类型契约测试

本期不交付（adapter 层 T16 已覆盖 `ListLogContextResponse` ↔ DTO 映射）。

### 部署后冒烟

`scripts/smoke/smoke-query_lts_log_context.sh`（本期不交付，列为遗留）：

1. 已知 line_num + cursor_time + size=10 → 返回非空 `logs`，长度约 20（前 10 + 后 10）
2. 缺 cursor_time → `INVALID_PARAM`
3. 不存在的 `log_group_id` → `UPSTREAM_ERROR` 或 `UPSTREAM_AUTH_FAILED`

## 7. 验收标准（DoD）

- [ ] `LtsLogContextToolTest` 3 条 UT 通过
- [ ] `LtsLogContextServiceTest` 9 条 UT 通过
- [ ] MCP Inspector 看到 `query_lts_log_context`，description 正确
- [ ] `lts-readonly` RateLimiter 实际生效（无新限流配置）
- [ ] 日志含入参摘要 / 耗时 / `upstream_trace_id`
- [ ] Micrometer 指标 `mcp_tool_invocation` 可见
- [ ] 部署后冒烟脚本（本期未交付，列入遗留）
- [ ] README 含使用示例（本期未交付）
- [ ] Checkstyle 0 violations
