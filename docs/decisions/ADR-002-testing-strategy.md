# ADR-002: 测试策略 — 无网络环境下的契约保障

- 状态: Accepted
- 日期: 2026-05-21

## Context

本项目目标部署在华为云贵阳一 region。开发机、CI runner、研发网均无法访问华为云 OpenAPI（仅生产 Pod 在贵阳一可调通）。

如果不能打真实 API 跑 contract test，纯 mock 测试会让"SDK 字段改名 / 枚举值未文档化 / 错误码不一致"这类问题留到部署后才暴露，AI 写得越多返工越大。

## Decision

采用 **三层防御 + 部署后冒烟** 的策略：

### Layer 1: 单元测试（完全 mock SDK）

- 覆盖业务分支、参数校验、错误码映射
- 不依赖任何网络
- 每个 spec 中的 UT-XX 用例必须 1:1 对应一个测试方法

### Layer 2: 类型契约测试（TC）

- 反射 SDK 类的关键字段，断言字段存在
- 用华为云文档样例 JSON 反序列化到 SDK 类，断言无 null
- 作用：SDK 升级如果改字段名 / 删字段，编译期可能发现不了（Builder 模式 + 反射场景），TC 会在 CI 暴露

### Layer 3: 录制回放（预留 profile）

- 预留 `mvn test -Precord` profile + WireMock 回放机制
- 未来若有跳板机或临时联网条件时，跑此 profile 真实打 API 并录制响应到 `src/test/resources/cassettes/`
- 当前不强制使用

### Layer 4: 部署后冒烟

- `scripts/smoke/smoke-<tool>.sh`，每个 tool 一份
- 部署到贵阳后人工或定时触发
- 真实调用每个 tool，断言关键路径

### 启动期校验

- AK/SK 缺失 → 启动 fail-fast，readiness 失败
- 这是最早能暴露"凭证错"的兜底

## Consequences

- ✅ CI 跑得快，无网络依赖
- ✅ 三层防御能拦掉大部分静默错误
- ⚠️ SDK API 行为变更（不是字段，是语义）仍只能靠冒烟发现
- ⚠️ 冒烟脚本依赖人工或定时触发，不在 PR 路径上，需要纪律

## Alternatives Considered

- **方案 A：本地录制 + CI 回放**：开发机能联网时录制 → CI 无网络回放。被开发机无网络否决。
- **方案 B：贵阳 runner**：贵阳服务器挂 CI agent，能直接打真实 API。受限于服务器权限，无法挂 agent。
- **方案 C 简版**：只做纯 mock。被否决，理由见 Context。
