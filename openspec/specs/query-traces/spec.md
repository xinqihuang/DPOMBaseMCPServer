# query-traces Specification

## Purpose
按 traceId/入口/时间窗/错误标志/最小耗时搜索 APM span 摘要，定位慢或出错的请求（trace 根因诊断链第 1 步）。

## Requirements

### Requirement: APM 调用链 span 检索

系统 SHALL 提供只读工具 `query_traces`，调用 APM SDK `ShowSpanSearch`，按多维条件搜索华为云 APM 调用链 span，返回分页结果 `{ total, spans[] }`。

工具 MUST 暴露以下入参：`businessId`（`x-business-id` 头部参数）/ `region`（资源区域）/ `startTimeString` / `endTimeString` / `traceId` / `source` / `hasError` / `timeUsedMin` / `page` / `pageSize`。该工具 MUST 为只读（`readOnlyHint=true`、`destructiveHint=false`、`idempotentHint=true`），不返回完整 span 树，不做日志 / 异常详情，不做跨账号，不做客户端聚合 / 排序。

#### Scenario: 按过滤参数透传查询
- **WHEN** Agent 传入任意合法过滤参数组合（traceId / source / 时间窗 / hasError / timeUsedMin）
- **THEN** 全部入参 SHALL 正确装配到 SDK `TraceSearchParam` body（`region` 取配置值必填）
- **AND** 返回 `spans[]` 与上游 `total`，span 顺序按上游返回顺序透传

#### Scenario: 默认分页
- **GIVEN** 调用未传 `page` / `pageSize`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 使用默认值 `page=1`、`pageSize=50`

### Requirement: 时间参数格式

系统 SHALL 将 `startTimeString` / `endTimeString` 作为字符串 `yyyy-MM-dd HH:mm:ss` 原样透传上游，不做本地时间解析或格式转换。响应内 span 的 `start_time` 为 UTC 毫秒时间戳（Long），系统 SHALL 原样投影，不转换为字符串。

#### Scenario: 时间入参原样透传
- **GIVEN** `startTimeString` 为 `yyyy-MM-dd HH:mm:ss` 格式字符串
- **WHEN** 调用工具
- **THEN** 系统 SHALL 原样写入 `TraceSearchParam.startTimeString`，不解析、不格式转换

### Requirement: business_id 解析与 fallback

系统 SHALL 将同一个有效 `businessId` 同时作为 HTTP `x-business-id` 头注入（`ShowSpanSearchRequest.setXBusinessId`）并写入请求体 `TraceSearchParam.bizId`。当入参 `businessId` 为 null 时，系统 SHALL 在 adapter 层回落到 `HuaweiCloudProperties.getApmBusinessId()` 配置默认值。

#### Scenario: businessId 回落配置默认值
- **GIVEN** 调用未传 `businessId`，但配置了 `apmBusinessId`
- **WHEN** 调用工具
- **THEN** 系统 SHALL 用配置默认值注入 `x-business-id` 头
- **AND** 系统 SHALL 将相同值写入请求体 `biz_id`

### Requirement: APM 端点区域与资源区域分离

系统 SHALL 使用 `huaweicloud.apm-region` 选择 APM SDK 端点，但 SHALL 使用工具参数 `region` 填充 `TraceSearchParam.region`；工具参数为空时 SHALL 回落到 `huaweicloud.region`。系统 MUST NOT 将 APM 端点区域作为被查询资源区域。

#### Scenario: 查询非 APM 端点区域中的应用
- **GIVEN** APM SDK 端点为 `cn-north-4`，被查询应用位于 `cn-north-9`
- **WHEN** Agent 传入 `region=cn-north-9` 或主资源区域配置为 `cn-north-9`
- **THEN** `TraceSearchParam.region` SHALL 为 `cn-north-9`
- **AND** APM SDK 客户端 SHALL 继续连接 `cn-north-4` 端点

### Requirement: 输入校验

系统 SHALL 在 service 层校验：`page` MUST >= 1；`pageSize` MUST ∈ [1, 500]；`timeUsedMin`（如提供）MUST >= 0。违反任一条 SHALL 返回 `INVALID_PARAM` 且不发起上游调用。其余字段透传上游，不预校验取值。

#### Scenario: page 越界
- **WHEN** `page` < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

#### Scenario: pageSize 越界
- **WHEN** `pageSize` > 500 或 < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

#### Scenario: timeUsedMin 为负
- **WHEN** `timeUsedMin` < 0
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`，不发起上游调用

### Requirement: span 响应投影

响应 DTO `ApmSpan` SHALL 从 SDK `ClientSpanInfo` 投影，取 `getSpanInfoList()` 为 span 列表、`getTotal()` 为顶层 `total`（可能为 null）。`tags` SHALL 始终非 null（adapter 用 `Map.of()` 兜底），便于 Agent 端直接遍历键集无需判空。

#### Scenario: tags 兜底非 null
- **GIVEN** 上游某 span 的 `getTags()` 返回 null
- **WHEN** adapter 投影该 span
- **THEN** `ApmSpan.tags` SHALL 为空 Map（非 null）

#### Scenario: total 可空透传
- **WHEN** 上游 `getTotal()` 返回 null
- **THEN** 系统 SHALL 将 `total` 原样置为 null，不抛异常

### Requirement: 上游异常映射

系统 SHALL 将 APM SDK 异常映射到统一 `ErrorCode`，不让 SDK 异常透传到 MCP 层；失败响应 SHALL 携带 `error_code` / `error_message` / `retryable` / `upstream_trace_id`（可空）。错误码映射与 `list_alarms` 一致（INVALID_PARAM / UPSTREAM_* / TIMEOUT / INTERNAL）。

#### Scenario: 限流映射
- **WHEN** 上游返回 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`，`retryable=true`

#### Scenario: 鉴权失败映射
- **WHEN** 上游返回 401/403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`，`retryable=false`，不重试

### Requirement: business_id 双位置装配

系统 SHALL 将有效 `businessId` 同时写入请求头 `x-business-id` 与请求体 `biz_id`，两处 MUST 使用同一个值。

#### Scenario: 查询指定应用的 span
- **WHEN** Agent 传入 `businessId=111092`
- **THEN** 请求头 `x-business-id` SHALL 为 `111092`
- **AND** 请求体 `biz_id` SHALL 为 `111092`
