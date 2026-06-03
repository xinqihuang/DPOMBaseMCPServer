# Spec: query_lts_logs

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 在事故排查时按时间区间 / 关键字 / 标签 / SQL 查询某个 LTS 日志流的原始日志。

典型场景:
- Agent 收到 CES 告警后，按时间窗到对应日志流里搜 `ERROR` / `Exception` / 自定义关键字
- Agent 知道某容器实例（`labels.containerName=...`）某时段的全部日志
- Agent 用 SQL 在 LTS 结构化日志上做分析（`is_analysis_query=true`，例如 5xx 占比 / TOP N）
- Agent 拿到一条目标日志后，用其 `line_num` + `cursor_time` 调 **query_lts_log_context**（T18）拉前后上下文

定位：LTS 第一条业务 tool。**前置**：Agent 须从 CMDB / 上层 prompt context 拿到 `log_group_id` 与 `log_stream_id`（本期不提供 `list_log_streams` 工具）。

## 2. 范围边界

**做**:
- 在给定 `log_group_id` + `log_stream_id` 下按时间区间 / 关键字 / 标签 / SQL 检索日志
- 支持 `line_num + __time__` 游标分页（与 SDK 一致）以及 `scroll_id` 模式
- 支持分析模式：`is_analysis_query=true` 时响应通过 `analysis_logs` 透传 SQL 结果
- 入参做 service 层校验（必填项 / 时间区间合法 / limit 范围 / search_type 取值）
- 上游异常映射到 `ErrorCode`，trace id 透传

**不做**:
- 不返回 trace / metric / 拓扑（用 APM / CES 工具）
- 不提供 `list_log_streams` / `list_log_groups`（须从外部获取）
- 不做客户端结果分桶 / 关键字高亮拼装（高亮交给上游 `highlight=true`）
- 不做 SQL 语法校验（交给上游）
- 不做跨 region / 跨 projectId

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `query_lts_logs`
- description（Agent 看到的，决定是否调用）:

  > Search raw logs from a Huawei Cloud LTS (Log Tank Service) log stream
  > by time range, keyword, labels, or SQL. Use this when investigating an
  > incident to locate ERROR/Exception/specific log entries within a known
  > log_group_id + log_stream_id. Returns log content + line_num cursor so
  > the agent can call query_lts_log_context to fetch surrounding entries.
  > Set is_analysis_query=true with a SQL `query` to run structured analysis
  > (top-N / counts); results then come back via analysis_logs. 'start_time'
  > / 'end_time' are UTC millis; pagination is cursor-based via line_num
  > + cursor_time, or scroll_id.

- annotations:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `log_group_id` | string | 是 | — | LTS 日志组 id |
| `log_stream_id` | string | 是 | — | LTS 日志流 id |
| `start_time_millis` | long | 否 | null | 搜索起始时间，UTC 毫秒；与 `end_time_millis` 配对使用 |
| `end_time_millis` | long | 否 | null | 搜索结束时间，UTC 毫秒；必须严格大于 `start_time_millis` |
| `labels` | map<string,string> | 否 | null | 日志标签过滤，AND 关系 |
| `keywords` | string | 否 | null | 关键字（与 `query` 互斥逻辑可由 Agent 自决，上游不强制） |
| `query` | string | 否 | null | SQL / 表达式查询语句 |
| `is_analysis_query` | bool | 否 | null | `true` 时走 SQL 分析模式，响应通过 `analysis_logs` 返回 |
| `is_count` | bool | 否 | null | `true` 时只返回 `count` |
| `limit` | int | 否 | null | 单页大小，[1, 5000]（SDK 限制） |
| `is_desc` | bool | 否 | null | 是否倒序；首次查询不传则上游默认正序 |
| `highlight` | bool | 否 | null | 是否高亮命中关键字 |
| `is_iterative` | bool | 否 | null | 是否启用迭代查询 |
| `search_type` | string | 否 | null | 分页方向：`forwards` / `backwards`；首次查询传 null（SDK 默认 `init`） |
| `line_num` | string | 否 | null | 游标分页：上次结果尾行的 `line_num` |
| `cursor_time` | string | 否 | null | 游标分页：与 `line_num` 配对的 `__time__` |
| `scroll_id` | string | 否 | null | scroll API 分页 id |

**输入校验规则**（service 层执行）:
- `log_group_id` / `log_stream_id` 必填且非空白 → 否则 `INVALID_PARAM`
- `start_time_millis` 与 `end_time_millis` 同时给时，要求 `start < end` → 否则 `INVALID_PARAM`
- `limit` 非 null 时必须在 `[1, 5000]` → 否则 `INVALID_PARAM`
- `search_type` 非 null 时必须为 `forwards` / `backwards` → 否则 `INVALID_PARAM`
- `line_num` 与 `cursor_time` 必须**同时给或同时不给**（成对游标）→ 单边给抛 `INVALID_PARAM`

### 3.3 输出契约（成功）

```json
{
  "count": 12,
  "logs": [
    {
      "content": "2026-06-03T10:15:23Z ERROR [order-svc] failed to ...",
      "line_num": "1717400000000000001",
      "labels": {
        "host_name": "ecs-i-abc",
        "container_name": "order-svc",
        "pod_name": "order-svc-7d8b-xyz"
      }
    }
  ],
  "is_query_complete": true,
  "analysis_logs": []
}
```

字段说明：
- `logs[].line_num`：纳秒时间戳格式的行号，可与 `cursor_time` 配对作为下次分页 cursor，也可作为 `query_lts_log_context` 的入参定位目标日志
- `is_query_complete=false` 表示上游结果被截断或仍在处理，需要继续分页
- 当 `is_analysis_query=true` 时，业务结果走 `analysis_logs`（每条元素的结构由 SQL 决定）；`logs` 可能为空

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
- **SDK 方法**: `listLogs(ListLogsRequest)`
- **SDK 版本**: 3.1.177（项目共用 `${huaweicloud-sdk.version}`）
- **HTTP**: `POST /v2/{project_id}/groups/{log_group_id}/streams/{log_stream_id}/content/query`

**字段映射**（adapter 层已实现，参见 T16）：

| MCP 输入 | SDK Request 字段 |
|---|---|
| `log_group_id` | path `log_group_id` |
| `log_stream_id` | path `log_stream_id` |
| `start_time_millis` | body `start_time`（**String.valueOf(long)**） |
| `end_time_millis` | body `end_time` |
| `labels` | body `labels` |
| `keywords` | body `keywords` |
| `query` | body `query` |
| `is_analysis_query` | body `is_analysis_query` |
| `is_count` | body `is_count` |
| `limit` | body `limit` |
| `is_desc` | body `is_desc` |
| `highlight` | body `highlight` |
| `is_iterative` | body `is_iterative` |
| `search_type` | body `search_type`（`QueryLtsLogParams.SearchTypeEnum.fromValue`） |
| `line_num` | body `line_num` |
| `cursor_time` | body `__time__` |
| `scroll_id` | body `scroll_id` |

## 5. 非功能要求

- **限流**: `lts-readonly` RateLimiter（10 QPS），与 LTS 其他读 API 共享
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避
- **超时**: SDK 传输层 10s
- **可观测**:
  - Micrometer: `mcp_tool_invocation{tool="query_lts_logs", result="success|error", error_code="..."}`
  - INFO 日志: `logGroupId` / `logStreamId` / `startTimeMillis` / `endTimeMillis` / `keywords` / `query` / `limit` / 耗时 / `upstream_trace_id`

## 6. 测试策略（DoD）

### 单元测试（mock service）

`LtsLogToolTest`：

| ID | 用例 | 期望 |
|---|---|---|
| UT-T1 | 全合法参数 | service 收到字段对齐的 request，返回值原样透传 |
| UT-T2 | service 抛 `InvalidParamException` | ErrorResponse / `INVALID_PARAM` / `retryable=false` |
| UT-T3 | service 抛 `UpstreamException(429)` | ErrorResponse / `UPSTREAM_THROTTLED` / `retryable=true` / trace id 透传 |

`LtsLogServiceTest`（mock adapter）：

| ID | 用例 | 期望 |
|---|---|---|
| UT-S1 | `request == null` | INVALID_PARAM |
| UT-S2 | `log_group_id` 空白 | INVALID_PARAM |
| UT-S3 | `log_stream_id` 空白 | INVALID_PARAM |
| UT-S4 | `start_time_millis >= end_time_millis` | INVALID_PARAM |
| UT-S5 | `limit = 0` 或 `limit = 5001` | INVALID_PARAM |
| UT-S6 | `search_type = "random"` | INVALID_PARAM |
| UT-S7 | `line_num` 给但 `cursor_time` 不给 | INVALID_PARAM |
| UT-S8 | 全合法 | 委托 adapter 调用，返回 adapter 响应 |

### 类型契约测试

本期不交付（adapter 层已通过 UT-01 ~ UT-09 覆盖 SDK ↔ DTO 映射）。

### 部署后冒烟

`scripts/smoke/smoke-query_lts_logs.sh`（本期不交付，列为遗留）：

1. 已知日志流 + 时间窗 + `keywords=ERROR` → 返回非空 `logs`
2. `start_time_millis > end_time_millis` → `INVALID_PARAM`
3. 不存在的 `log_group_id` → `UPSTREAM_ERROR` 或 `UPSTREAM_AUTH_FAILED`（看上游具体响应）

## 7. 验收标准（DoD）

- [ ] `LtsLogToolTest` 3 条 UT 通过
- [ ] `LtsLogServiceTest` 8 条 UT 通过
- [ ] MCP Inspector 看到 `query_lts_logs`，description 正确
- [ ] `lts-readonly` RateLimiter 实际生效
- [ ] 日志含入参摘要 / 耗时 / `upstream_trace_id`
- [ ] Micrometer 指标 `mcp_tool_invocation` 可见
- [ ] 贵阳 / 香港冒烟脚本（本期未交付）
- [ ] README 含使用示例（本期未交付）
- [ ] Checkstyle 0 violations
