# Spec: <tool_name>

> 状态: Draft | Approved | Deprecated · 版本: vX.Y · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

<这个 tool 解决什么问题，给 Agent 在什么场景下用>

典型场景:
- 场景 1
- 场景 2

定位: 它和其他 tool 的关系（前置 / 后置 / 互补）。

## 2. 范围边界

**做**:
- ...

**不做**:
- ...

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `<tool_name>`
- description（Agent 看到的）:
  > <一段 prompt，让 Agent 能正确判断什么时候调用>
- annotations:
  - `readOnlyHint=true|false`
  - `destructiveHint=true|false`
  - `idempotentHint=true|false`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| ... | ... | ... | ... | ... |

**输入校验规则**:
- ...

### 3.3 输出契约（成功）

```json
{
  ...
}
```

字段说明:
- ...

### 3.4 输出契约（失败）

```json
{
  "error_code": "INVALID_PARAM | UPSTREAM_THROTTLED | UPSTREAM_AUTH_FAILED | UPSTREAM_ERROR | TIMEOUT | INTERNAL",
  "error_message": "...",
  "upstream_trace_id": "...",
  "retryable": true | false
}
```

错误码映射:

| 上游情况 | error_code | retryable |
|---|---|---|
| ... | ... | ... |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `...`
- **SDK 方法**: `...(...)`
- **SDK 版本**: v3.1.196

**字段映射**:

| MCP 输入 | SDK Request 字段 |
|---|---|
| ... | ... |

**AI 容易写错的点**（实现时务必注意）:
1. ...

## 5. 非功能要求

- **限流**: RateLimiter key = `...`，默认 QPS = ...
- **重试**: ...
- **超时**: ...
- **可观测**: 指标 / 日志要求

## 6. 测试策略（Definition of Done）

### 单元测试 (mock SDK)

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | ... | ... |

### 类型契约测试

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | ... | ... |

### 部署后冒烟

`scripts/smoke/smoke-<tool>.sh`:

1. ...

## 7. 验收标准 (DoD)

- [ ] 所有 UT 用例通过
- [ ] 所有 TC 用例通过
- [ ] MCP Inspector 能看到，description 正确
- [ ] 配置项可调
- [ ] 日志含入参摘要 / 耗时 / upstream trace id
- [ ] Micrometer 指标可见
- [ ] 冒烟脚本通过
- [ ] README 含使用示例
