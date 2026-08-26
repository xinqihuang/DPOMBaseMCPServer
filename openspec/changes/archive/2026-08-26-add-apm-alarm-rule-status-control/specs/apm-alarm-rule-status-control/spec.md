## Purpose

为 DPOMBaseMCPServer 的内部调用方提供稳定、可测试的华为云 APM 整条告警规则启停能力，弥补 Java SDK 未生成该接口的问题，同时保持认证信息隔离和统一失败语义。

## ADDED Requirements

### Requirement: 整条 APM 告警规则状态更新

系统 SHALL 提供内部 Java 能力，调用华为云 APM `PUT /v2/alarm-center/rule/update-rule-disable`，接收 `alarm_rule_id` 与 `enable` 两个必填参数。`enable=false` SHALL 关闭该 ID 对应的整条告警规则，`enable=true` SHALL 启用该 ID 对应的整条告警规则；系统 MUST NOT 将规则 ID 解释为告警事件 ID、实例 ID 或模板 ID。

#### Scenario: 关闭整条规则
- **WHEN** 调用方传入合法的 `alarm_rule_id` 且 `enable=false`
- **THEN** 系统 SHALL 发起一次 `PUT` 请求，并将两个参数编码为查询参数
- **AND** 成功响应 SHALL 明确返回被操作的规则 ID、目标状态与成功标志

#### Scenario: 重新启用整条规则
- **WHEN** 调用方传入合法的 `alarm_rule_id` 且 `enable=true`
- **THEN** 系统 SHALL 发起一次 `PUT` 请求，并返回启用成功结果

### Requirement: 输入校验与无副作用失败

`alarm_rule_id` MUST 大于 0，`enable` MUST 非 null。输入不合法时系统 SHALL 返回 `INVALID_PARAM`，且 MUST NOT 发起上游请求。

#### Scenario: 非法规则 ID
- **WHEN** `alarm_rule_id` 为 null、0 或负数
- **THEN** 系统 SHALL 返回 `INVALID_PARAM` 且不调用华为云

#### Scenario: 缺少目标状态
- **WHEN** `enable` 为 null
- **THEN** 系统 SHALL 返回 `INVALID_PARAM` 且不调用华为云

### Requirement: 华为云认证与敏感信息保护

系统 SHALL 使用既有华为云凭据链对请求进行认证，并使用独立配置的 APM endpoint/region 选择目标服务。AK、SK、security token、Authorization 及其他认证头 MUST NOT 出现在 API 返回值、异常消息或日志中。

#### Scenario: 请求经过认证
- **GIVEN** 已配置合法的华为云凭据和 APM region
- **WHEN** 更新规则状态
- **THEN** 出站请求 SHALL 包含华为云接受的认证信息
- **AND** 日志 SHALL 仅记录非敏感的规则 ID、目标状态和请求追踪 ID

### Requirement: 成功响应契约

华为云返回 HTTP 200 且响应体字段 `ok` 等于 `ok` 时，系统 SHALL 判定操作成功。HTTP 200 但响应体缺失、不可解析或 `ok` 值异常时，系统 MUST 返回 `UPSTREAM_ERROR`，不得误报成功。

#### Scenario: 正常成功响应
- **WHEN** 上游返回 HTTP 200 和 `{ "ok": "ok" }`
- **THEN** 系统 SHALL 返回成功结果

#### Scenario: 异常的 200 响应
- **WHEN** 上游返回 HTTP 200 但响应体不是有效的成功契约
- **THEN** 系统 SHALL 返回 `UPSTREAM_ERROR` 且 `retryable=true`

### Requirement: 上游错误映射

系统 SHALL 将 HTTP、连接及超时错误映射到统一错误码，且在可用时携带华为云 `X-Request-Id`。HTTP 400 SHALL 映射为 `INVALID_PARAM`、401/403 SHALL 映射为 `UPSTREAM_AUTH_FAILED`、429 SHALL 映射为 `UPSTREAM_THROTTLED`、超时 SHALL 映射为 `TIMEOUT`，其他非成功响应 SHALL 映射为 `UPSTREAM_ERROR`。

#### Scenario: 鉴权失败
- **WHEN** 上游返回 HTTP 401 或 403
- **THEN** 系统 SHALL 返回 `UPSTREAM_AUTH_FAILED`、`retryable=false` 并保留可用的请求追踪 ID

#### Scenario: 限流
- **WHEN** 上游返回 HTTP 429
- **THEN** 系统 SHALL 返回 `UPSTREAM_THROTTLED`、`retryable=true` 并保留可用的请求追踪 ID

#### Scenario: 请求超时
- **WHEN** 上游连接或读取超时
- **THEN** 系统 SHALL 返回 `TIMEOUT`、`retryable=true`

### Requirement: 不自动暴露为 MCP 写工具

本能力 SHALL 只作为 APM adapter 的内部 Java API 交付。系统 MUST NOT 因引入该封装而自动注册 MCP tool、启动定时关闭任务，或在应用启动及测试时修改真实云上规则。

#### Scenario: 应用正常启动
- **WHEN** DPOMBaseMCPServer 启动且未有内部调用方显式调用状态更新 API
- **THEN** 系统 SHALL 不发起规则状态变更请求
