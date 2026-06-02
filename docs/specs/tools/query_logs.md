# Spec: query_logs

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 在事故定位 / 巡检过程中检索 AOM (Application Operations Management) 的日志记录，覆盖应用日志 / 主机日志 / 自定义日志三类，支持关键字（含通配符 + 布尔组合）与时间区间过滤。

典型场景:
- Agent 在告警后 5 分钟内捞应用错误堆栈：`category=app_log` + `key_word=*ERROR*`
- 巡检主机文件系统异常：`category=node_log` + 自定义关键字
- Agent 已有可疑请求时间窗，按短窗口 + 关键字组合定位单次故障日志

定位:
- 这是查询型 tool，**不返回结构化字段**，将上游原始 `result` JSON 字符串透传给 Agent 自行解析（spec §2 "不做" 强调）
- 它和 `correlate_incident` 的 `aom_logs` 分支调用同一个底层 service，但单独调用更适合 Agent 想"只看日志"的窄场景

## 2. 范围边界

**做**:
- 按 `(category, startTime, endTime, keyWord)` 检索 AOM 日志
- 单页 [1, 1000]，默认 100；按 `lineNum` 倒序（可关）
- 关键字支持上游语法：精确 / 通配符 (`*foo*`) / 短语 / `&&` / `||`
- 透传上游 `result` JSON 字符串 + 错误码

**不做**:
- 不解析 `result` 字符串内部结构（避免随上游 schema 变化频繁调整）
- 不做翻页 / 不做 `lineNum` 游标管理（MVP 不暴露）
- 不支持跨 region / 跨 projectId
- 不做日志写入或日志组配置（只读）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `query_logs`（即 `@Tool(name = "query_logs")` 实际注册值）
- description（Agent 看到的）:

  > Query AOM (Application Operations Management) log records over a time
  > window. Useful when investigating an incident — find recent error log lines,
  > stack traces, or specific keywords from application, node, or custom logs in
  > Huawei Cloud. 'category' selects the log source. 'start_time'/'end_time' are
  > UTC milliseconds. 'key_word' supports exact, wildcard ('*ERR*'), phrase
  > search, and '&&'/'||' boolean combinators. Returns the raw upstream 'result'
  > JSON string for downstream parsing.

- annotations:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `category` | string | 是 | — | 日志类型，仅允许 `app_log` / `node_log` / `custom_log` |
| `startTime` | long | 是 | — | UTC 毫秒起点 |
| `endTime` | long | 是 | — | UTC 毫秒终点，**必须严格大于** `startTime` |
| `keyWord` | string | 否 | null | 关键字过滤；支持 `*` 通配符与 `&&` / `||` 组合 |
| `pageSize` | int | 否 | 100 | 单页大小 [1, 1000] |
| `isDesc` | bool | 否 | true | 是否按 lineNum 倒序（最新优先） |

**输入校验规则**（Service 层）:
- `request == null` → `INVALID_PARAM`
- `category` 不在 `{app_log, node_log, custom_log}` → `INVALID_PARAM`
- `startTime` / `endTime` 任一为 null → `INVALID_PARAM`
- `endTime <= startTime` → `INVALID_PARAM`
- `pageSize` 不在 [1, 1000] → `INVALID_PARAM`（紧凑构造已把 null 改为 100，但 Tool 端显式传 0 或 1001 会被拦下）

### 3.3 输出契约（成功）

```json
{
  "result": "<JSON 字符串，未经解析的上游 result 原文>",
  "errorCode": "SVCSTG_AMS_2000000",
  "errorMessage": null
}
```

字段说明:
- `result`: 上游 AOM `ListLogItems` 接口返回的 `result` 字段原值；通常本身是一段 JSON，**保持字符串形式透传**，由 Agent / 上层调用方自行 `JSON.parse`
- `errorCode`: 上游响应码，成功为 `SVCSTG_AMS_2000000`
- `errorMessage`: 上游响应信息，成功时通常为 `null`

### 3.4 输出契约（失败）

```json
{
  "error_code": "INVALID_PARAM | UPSTREAM_THROTTLED | UPSTREAM_AUTH_FAILED | UPSTREAM_ERROR | TIMEOUT | INTERNAL",
  "error_message": "human readable",
  "upstream_trace_id": "华为云返回的 X-Request-Id, 可为 null",
  "retryable": true | false
}
```

错误码映射:

| 上游情况 | error_code | retryable |
|---|---|---|
| 入参校验失败 | INVALID_PARAM | false |
| HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.aom.v2.AomClient`
- **SDK 方法**: `listLogItems(ListLogItemsRequest)`
- **SDK 版本**: v3.1.177（见 CLAUDE.md §1）
- **AOM API 文档**: ListLogItems（`type=querylogs`）

**字段映射**:

| MCP 输入 | SDK Request 字段 |
|---|---|
| 固定常量 | `withType("querylogs")` |
| `category` | `body.withCategory(String)` |
| `startTime` | `body.withStartTime(Long)` |
| `endTime` | `body.withEndTime(Long)` |
| `pageSize` | `body.withPageSizeSize(String)`（**SDK 字段名带双 Size**） |
| `isDesc` | `body.withIsDesc(Boolean)` |
| `keyWord` | `body.setKeyWord(String)`（仅在非 null 时调用） |

**AI 容易写错的点**:
1. **SDK 的 `withPageSizeSize` 字段名拼写**：双 `Size` 不是笔误，是上游 API 命名，**直接 String 字符串传递**而不是数字
2. **`ListLogItemsRequest.withType("querylogs")`** 必传：表示这是日志查询而非其他用途
3. **响应 `result` 是 String 不是 object**：DTO 字段保持 String，不要在 adapter 层做 `ObjectMapper.readTree` 解析（避免随上游 schema 改动断裂）
4. **`keyWord` 字段名是驼峰且首字母小写**：MCP 入参 `keyWord` 与 SDK `keyWord` 同名，**不是 `keyword`**

## 5. 非功能要求

- **限流**: 复用 `aom-readonly` RateLimiter（与 `list_aom_metrics` / `query_aom_metric_data` 共享配额）
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试，3 次指数退避 200ms / 800ms / 3.2s（`huaweicloud-retryable`）
- **超时**: 单次 SDK 调用 10s
- **可观测**:
  - Micrometer 指标: `mcp_tool_invocation{tool="query_logs", result="success|error", error_code="..."}`
  - 日志 INFO（adapter 起点）: `category` + `startTime` + `endTime` + `pageSize`
  - 日志 WARN（tool 兜底）: `errorCode` + `upstreamTraceId`

## 6. 测试策略（Definition of Done）

### 单元测试

本期未交付。后续建议矩阵：

Service 层（`AomLogServiceTest`）:

| ID | 用例 | 期望 |
|---|---|---|
| UT-S1 | request=null | INVALID_PARAM |
| UT-S2 | category 不在白名单 | INVALID_PARAM，消息含取值 |
| UT-S3 | startTime / endTime 任一 null | INVALID_PARAM |
| UT-S4 | endTime <= startTime | INVALID_PARAM |
| UT-S5 | pageSize=0 / 1001 | INVALID_PARAM |
| UT-S6 | 全合法 | 委托 adapter |

Adapter 层（`AomMetricsAdapterImplTest` 新增方法）:

| ID | 用例 | 期望 |
|---|---|---|
| UT-A1 | 全合法请求 | SDK Request `type="querylogs"`，body 字段对齐，`pageSizeSize=String.valueOf(pageSize)` |
| UT-A2 | keyWord=null | SDK `setKeyWord` 不被调用 |
| UT-A3 | SDK 返回 result + errorCode | DTO 三字段透传 |
| UT-A4 | SDK 抛 429 | 重试 3 次后 UPSTREAM_THROTTLED |
| UT-A5 | SDK 抛 401 | 不重试，UPSTREAM_AUTH_FAILED |

Tool 层（`AomLogToolTest`）:

| ID | 用例 | 期望 |
|---|---|---|
| UT-T1 | service 抛 InvalidParamException | ErrorResponse，INVALID_PARAM |
| UT-T2 | service 抛 UpstreamException(5xx) | ErrorResponse，UPSTREAM_ERROR + traceId |

### 类型契约测试

本期未交付。建议：

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | SDK `QueryBodyParam` 反射含 `category` / `startTime` / `endTime` / `keyWord` / `pageSizeSize` / `isDesc` |
| TC-02 | SDK `ListLogItemsResponse` 反射含 `result` / `errorCode` / `errorMessage` |

### 部署后冒烟

本期未交付。建议 `scripts/smoke/smoke-query_logs.sh`：

1. `category=app_log` + 最近 5 分钟 + 无关键字 → 断言 `errorCode=SVCSTG_AMS_2000000`
2. `category=invalid` → INVALID_PARAM
3. `endTime < startTime` → INVALID_PARAM

## 7. 验收标准（DoD）

- [x] Tool 注册类 `AomLogTool` 实现 + `@Tool(name="query_logs")` description 已经 MCP Inspector 验证
- [x] Service 层 `AomLogService` 5 项参数校验完整
- [x] Adapter 复用 `AomMetricsAdapterImpl`，限流 key `aom-readonly`，retry `huaweicloud-retryable`
- [x] 响应 `result` 以 String 形式透传，不在 adapter 层解析
- [x] 日志含 category / startTime / endTime / pageSize / upstream errorCode
- [x] 代码已合入 master（提交 `4c346d6`）
- [x] Checkstyle 0 violations
- [ ] Service / Adapter / Tool 层 UT（后续任务补）
- [ ] 类型契约测试 + 贵阳冒烟脚本 + Micrometer 指标 + README 示例（后续任务补）
