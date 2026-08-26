## Why

华为云 APM 已开放整条告警规则启停接口，但当前 Java SDK（包括项目使用的 3.1.x）没有对应模型和客户端方法，DPOMBaseMCPServer 因而无法通过统一适配层关闭或重新启用规则。需要在 APM adapter 内补齐这一 REST 能力，为后续受控故障处置提供基础。

## What Changes

- 在 `agentic-adapter-apm` 新增 APM 告警规则状态管理 adapter，封装 `PUT /v2/alarm-center/rule/update-rule-disable`。
- 暴露以 `alarmRuleId` 和 `enable` 为参数的 Java API；`enable=false` 关闭整条规则，`enable=true` 重新启用整条规则。
- 复用现有华为云 AK/SK、APM region、超时和统一异常语义，不记录凭据或认证头。
- 校验规则 ID，完整处理成功、参数错误、鉴权失败、限流、超时及其他上游失败。
- 增加 HTTP 契约和 adapter 单元测试，验证请求方法、路径、查询参数、认证以及响应映射。
- 本变更不新增 MCP tool，不会在部署或测试过程中自动修改真实云上规则。

## Capabilities

### New Capabilities

- `apm-alarm-rule-status-control`: 在 APM adapter 中通过受认证 REST 调用启用或关闭整条 APM 告警规则，并提供稳定的内部 Java 契约与统一错误映射。

### Modified Capabilities

无。

## Impact

- 主要影响 `agentic-adapter/agentic-adapter-apm` 的接口、实现、配置和测试。
- 复用 `agentic-common` 中的华为云配置、凭据和统一异常设施；若 SDK core 无法直接承载该未生成 API，则仅使用 JDK 21 HTTP 能力及华为云 SDK core 的签名组件，不引入第三方 HTTP 客户端。
- 这是对项目当前“只读监控查询”基线的一个显式、窄范围例外：写能力只存在于 adapter 内，不注册到 MCP，不改变现有只读工具行为。
- `alarmRuleId` 标识整条共享规则；调用 `enable=false` 会影响该规则覆盖的全部服务与实例，而不是单个告警事件。
