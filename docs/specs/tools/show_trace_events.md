# Tool Spec: `show_trace_events`

> 状态: Ready · API 版本: **APM v1 `ShowTraceEvents`（GET /v1/apm2/openapi/view/trace/get-trace-events）** · SDK: `huaweicloud-sdk-apm:3.1.177`
> 权威 schema：SDK sources jar `ShowTraceEventsRequest / ShowTraceEventsResponse / SpanEventInfo / DiscardInfo`（已逐字段核对）。

## 1. 定位

traceId 根因诊断链第 3 步：获取一个 trace 的**全部调用链事件序列**（每个 span 内部的方法调用 /
SQL / 远程调用事件，含耗时、状态、错误标记与 `event_id`）。从「哪个组件慢/错」（query_traces /
get_service_topology）下钻到「组件内哪一步慢/错」的桥梁：

```
query_traces → get_service_topology → show_trace_events
  → show_event_detail → show_clob_detail
```

实时数据，**不缓存**。

## 2. 请求映射

| 工具入参 | 类型 | 必填 | SDK 目标 |
|---|---|---|---|
| `trace_id` | String | 是 | query `trace_id`（来自告警 payload 或 query_traces 的返回，禁止编造） |

## 3. 响应映射（SDK → DTO，§4.1 无损）

`ShowTraceEventsResponse.span_event_list` → `List<ApmSpanEvent>`。

`SpanEventInfo` → `ApmSpanEvent`（**38 字段全量**，与 `show_event_detail` 共享同一 record）：
env_name / app_name / indent / region / host_name / ip_address / instance_name / event_id /
**next_spanId**（SDK 原样混合命名，照抄）/ source_event_id / method / children_event_count /
discard（List\<ApmDiscardInfo\>）/ argument / attachment（Map）/ global_trace_id / global_path /
trace_id / span_id / env_id / instance_id / app_id / biz_id / domain_id / source / real_source /
start_time / time_used / code / class_name / is_async / tags（Map，含 SQL/异常等关键线索；
超长内容以 clob 引用出现，用 show_clob_detail 取全文）/ has_error / error_reasons / type /
http_method / biz_code / id。

`DiscardInfo` → `ApmDiscardInfo`：type / count / **totalTime**（SDK 原样驼峰，照抄）。

## 4. 错误与校验

`trace_id` 空白 → `INVALID_PARAM`；限流 `apm-readonly`，API 名 `apm.showTraceEvents`。

## 5. 测试策略

- TC：样本含一条全字段事件 + 一条最小事件，断言 38 + 3 字段全覆盖。
- UT（service）：trace_id 必填；合法请求透传。UT（tool）：成功透传 / 异常 → ErrorResponse。
