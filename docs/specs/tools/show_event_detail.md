# Tool Spec: `show_event_detail`

> 状态: Ready · API 版本: **APM v1 `ShowEventDetail`（GET /v1/apm2/openapi/view/trace/get-event-detail）** · SDK: `huaweicloud-sdk-apm:3.1.177`
> 权威 schema：SDK sources jar `ShowEventDetailRequest / ShowEventDetailResponse / SpanEventInfo`（已逐字段核对）。

## 1. 定位

traceId 根因诊断链第 4 步：获取单个调用链事件的完整详情（tags 含异常类名 / message / SQL /
HTTP 状态等根因数据）。入参四元组**必须来自 `show_trace_events` 的真实响应**（§4.3 (b)
发现真值转发的标准案例），禁止凭先验编造。实时数据，**不缓存**。

## 2. 请求映射（全部 query 参数，全部必填）

| 工具入参 | 类型 | SDK 目标 | 来源 |
|---|---|---|---|
| `trace_id` | String | query `trace_id` | show_trace_events 响应 |
| `span_id` | String | query `span_id` | 同上 |
| `event_id` | String | query `event_id` | 同上（事件的 `event_id` 或 `id`） |
| `env_id` | Long | query `env_id` | 同上（事件的 `env_id`） |

## 3. 响应映射（SDK → DTO，§4.1 无损）

`ShowEventDetailResponse.event_info` → `ApmSpanEvent`（复用 show_trace_events 的 38 字段
record，单对象形态；详情形态下 `tags` / `attachment` 内容更全，超长字段以 clob 引用出现）。

## 4. 错误与校验

四个入参任一缺失/空白 → `INVALID_PARAM`；限流 `apm-readonly`，API 名 `apm.showEventDetail`。

## 5. 测试策略

- TC：样本 event_info 全字段断言 + 请求侧四 query 参数装配断言。
- UT（service）：四参数必填逐一校验；UT（tool）：成功透传 / 异常 → ErrorResponse。
