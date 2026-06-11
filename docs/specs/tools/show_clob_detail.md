# Tool Spec: `show_clob_detail`

> 状态: Ready · API 版本: **APM v1 `ShowClobDetail`（POST /v1/apm2/openapi/view/metric/get-clob-detail）** · SDK: `huaweicloud-sdk-apm:3.1.177`
> 权威 schema：SDK sources jar `ShowClobDetailRequest / GetClobDetailParam / ShowClobDetailResponse`（已逐字段核对）。

## 1. 定位

traceId 根因诊断链第 5 步（终点）：事件详情中超长字段（**完整异常堆栈、完整 SQL 文本**）
不内联返回，以 clob 引用存储；本工具按 `clob_id` 取回全文。`clob_id` **必须来自
`show_event_detail` / `show_trace_events` 响应的 tags/attachment 中出现的 clob 引用**
（§4.3 (b)），禁止编造。实时数据，**不缓存**。

## 2. 请求映射

| 工具入参 | 类型 | 必填 | SDK 目标 | 说明 |
|---|---|---|---|---|
| `business_id` | Long | 否 | header `x-business-id` | 为 null 回落 `huaweicloud.apm-business-id` 配置（T23/T28 同款约定） |
| `env_id` | Long | 是 | body `env_id` | 事件所属环境 id，来自事件数据 |
| `clob_id` | String | 是 | body `clob_id` | clob 引用 id，来自事件 tags/attachment |

## 3. 响应映射（SDK → DTO，§4.1 无损）

`ShowClobDetailResponse` → `ApmClobDetailResponse`：`clob_string`（String，全文）——1 字段全量。

## 4. 错误与校验

`env_id` 为 null 或 `clob_id` 空白 → `INVALID_PARAM`；限流 `apm-readonly`，API 名 `apm.showClobDetail`。

## 5. 测试策略

- TC：响应 clob_string 断言 + 请求侧 header/body 装配断言（含 business_id 配置回落）。
- UT（service）：env_id/clob_id 必填；UT（tool）：成功透传 / 异常 → ErrorResponse。
