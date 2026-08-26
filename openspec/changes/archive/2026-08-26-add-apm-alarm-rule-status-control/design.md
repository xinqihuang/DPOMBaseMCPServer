## Context

参见 [proposal.md](proposal.md) 的动机。项目当前使用 `huaweicloud-sdk-apm` 3.1.x，但该 SDK 未生成 `UpdateAlarmRuleStatus` 方法和模型；SDK core 已提供 `HttpRequest`、`HttpClient`、`BasicCredentials.syncProcessAuthRequest` 等底层能力。现有 APM adapter 使用独立的 `apmRegion` 和 SDK 默认 endpoint，错误由 common 层统一建模。

本变更是当前只读基线的窄范围例外。它只建立 adapter API，不延伸到 monitoring 或 MCP，因此不会让 Agent 自动获得关闭规则的权限。

## Goals / Non-Goals

**Goals:**

- 在 APM adapter 内提供可注入、可替换、可单元测试的整条规则启停接口。
- 复用 SDK core 的 AK/SK 认证处理和 HTTP 配置，行为尽量与生成的 APM SDK 客户端一致。
- 对请求、成功体和失败状态做严格校验，保持统一错误语义。
- 为未来受控 service/MCP 操作保留稳定边界。

**Non-Goals:**

- 不新增 MCP tool、审批流或自动恢复编排。
- 不查询规则详情，也不实现针对单个实例、事件或时间窗的静默。
- 不在测试中调用或改变真实华为云规则。
- 不升级 `huaweicloud-sdk-apm` 版本来等待一个当前仍不存在的生成接口。

## Decisions

### 1. 新建独立的规则管理 adapter

新增 `ApmAlarmRuleAdminAdapter`，方法接收一个自定义请求 DTO 并返回自定义响应 DTO；实现类与现有 `ApmAlarmAdapter` 并列。状态查询和告警数据查询继续留在原 adapter，避免把写入职责混入只读接口。

备选方案是直接给 `ApmAlarmAdapter` 加方法。未采用，因为这会让已明确为只读查询的接口同时承担高影响写操作，并扩大现有调用方误用面。

### 2. 使用 SDK core 的 HTTP 与凭据处理链

实现通过 `HttpRequest` 构造 `PUT /v2/alarm-center/rule/update-rule-disable`，将 `alarm_rule_id` 与 `enable` 作为 query 参数；使用 `BasicCredentials.syncProcessAuthRequest` 生成华为云认证请求，再由 SDK core `HttpClient` 发送。endpoint 从 `ApmRegion.valueOf(apmRegion).getEndpoints()` 解析或由专用可选配置覆盖，禁止硬编码 region、host 或测试账号。

这条路径复用现有 AK/SK/projectId，不要求新增用户名/密码来换取 IAM Token。虽然接口文档以 `X-Auth-Token` 描述认证，华为云开放 API 的 SDK core 认证链可生成等价的 AK/SK 签名请求；真实环境验证将作为人工显式步骤，不纳入自动测试。

备选方案一是使用 JDK `HttpClient` 并手写签名。未采用，因为自实现签名容易在 query 编码、canonical headers 和时钟处理上出错。备选方案二是新增 IAM 用户名/密码并自行缓存 Token。未采用，因为现有部署只有 AK/SK，而且会引入另一组长期敏感凭据与 Token 生命周期管理。

### 3. 抽出可替换的传输边界

将“构造并认证请求”与“同步发送 HTTP”拆成小型内部组件/Bean，生产配置使用 SDK core 实现，测试注入 fake transport 捕获 `HttpRequest` 并返回构造的 `HttpResponse`。这样测试能精确断言 method/path/query/auth，而无需监听本地端口或引入第三方 mock server。

### 4. 严格解析成功体

响应 DTO 只承载上游唯一公开字段 `ok`，adapter 对 HTTP 200 后的 JSON 做严格检查，仅 `ok == "ok"` 视为成功。对外结果额外带回调用输入中的 `alarmRuleId` 和 `enable`，便于上层审计，但不把它们伪装成上游返回字段。

### 5. 沿用统一异常模型并显式分类

错误映射表如下：

| 来源 | ErrorCode | retryable |
|---|---|---:|
| 本地参数非法、HTTP 400 | `INVALID_PARAM` | false |
| HTTP 401/403 | `UPSTREAM_AUTH_FAILED` | false |
| HTTP 429 | `UPSTREAM_THROTTLED` | true |
| 连接/读取超时 | `TIMEOUT` | true |
| 其他 HTTP/响应契约异常 | `UPSTREAM_ERROR` | true |
| 未预期本地错误 | `INTERNAL` | false |

从响应头读取 `X-Request-Id`，但异常内容不得包含请求认证头或完整原始响应体。

## Risks / Trade-offs

- [关闭共享规则会影响多个服务和实例] → adapter API 和 Javadoc 明确使用“整条规则”语义；本变更不暴露 MCP tool，实际调用方仍需显式传入规则 ID。
- [APM 未生成接口可能发生服务端契约漂移] → 对 path、query 和 `{ "ok": "ok" }` 建立契约测试；非预期成功体 fail closed。
- [文档认证描述与 AK/SK 实际支持存在环境差异] → 使用 SDK core 标准认证链；上线前以专用测试规则做一次显式关闭/重开验证。如目标环境只接受 IAM Token，再以不改变 adapter 公共契约的方式替换认证 transport。
- [重试写请求可能重复执行] → adapter 本身不自动重试；调用方仅可基于幂等的目标状态决定是否重试，且需记录审计。

## Migration Plan

1. 发布仅包含新 Bean/API 的版本，现有调用路径不变。
2. 在非生产环境用专用测试规则执行一次 `false` 后 `true` 的人工验证，并通过规则查询或控制台核对最终状态。
3. 若认证或接口契约不兼容，回滚应用版本即可；因为没有自动调用方，部署本身不会留下云上状态变化。
