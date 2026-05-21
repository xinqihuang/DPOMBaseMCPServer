# Spec: list_ces_metrics

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 能够发现「某个资源 / 某个 namespace 下有哪些可查询的 CES 指标」。

典型场景:
- Agent 收到告警, 要查告警对应资源还能看哪些相关指标 -> 列该资源所有 metrics
- Agent 做巡检, 要知道某 namespace 下都有什么指标 -> 按 namespace 列
- Agent 不确定指标名拼写 -> 按 namespace 浏览
- 后续 `query_metric_data` 必须先知道有效的 (namespace, metric_name, dimensions) 三元组

定位: 这是 `query_metric_data` / `batch_query_metric_data` 的前置发现 tool。

## 2. 范围边界

**做**:
- 查询 CES 已注册的指标定义列表 (不是指标数据点)
- 支持按 namespace / metric_name / 单个 dimension 过滤
- 支持分页 (marker 游标式)
- 单租户固定 region

**不做**:
- 不返回指标数据 (那是 `query_metric_data` 的职责)
- 不做 AOM Prometheus 指标 (那是 `list_aom_metrics`)
- 不做跨 region / 跨账号
- 不做多维度组合过滤 (华为云 ListMetrics 只支持 dim.0 一个入参)

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `list_ces_metrics`
- description (Agent 看到的, 决定它是否调用):

  > List available CES (Cloud Eye Service) metric definitions for Huawei Cloud
  > resources. Use this to discover which metrics can be queried for a given
  > namespace (e.g., SYS.ECS, SYS.RDS) or a specific resource. Returns metric
  > metadata (name, namespace, dimensions, unit), not actual data points.
  > Call query_metric_data afterwards to get values.

- annotations:
  - `readOnlyHint=true`
  - `destructiveHint=false`
  - `idempotentHint=true`

### 3.2 输入参数

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `namespace` | string | 否 | null | CES namespace, 如 `SYS.ECS` `SYS.RDS`。格式 `service.item`, service 须以大写字母开头, 长度 [3,32] |
| `metric_name` | string | 否 | null | 精确指标名, 如 `cpu_util`。长度 [1,64] |
| `dim_name` | string | 否 | null | 维度名 (CES 文档 `dim.0` 的 key 部分), 如 `instance_id` |
| `dim_value` | string | 否 | null | 维度值。仅当 `dim_name` 提供时生效, 且必须一起提供 |
| `limit` | int | 否 | 100 | 单页大小, [1, 1000] |
| `start` | string | 否 | null | 分页游标, 使用上一次返回的 `next_marker` |
| `order` | enum | 否 | "desc" | 排序, "asc" 或 "desc" |

**输入校验规则**:
- `dim_name` 与 `dim_value` 要么都给要么都不给, 单独给一个 -> `INVALID_PARAM`
- `limit` 超出 [1, 1000] -> `INVALID_PARAM`
- `namespace` 给了但格式不符合 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$` -> `INVALID_PARAM`
- `order` 不是 "asc" / "desc" -> `INVALID_PARAM`
- 所有字段都没给 -> 允许 (受 limit 约束)

### 3.3 输出契约 (成功)

```json
{
  "metrics": [
    {
      "namespace": "SYS.ECS",
      "metric_name": "cpu_util",
      "unit": "%",
      "dimensions": [
        {"name": "instance_id", "value": "d9112af5-6913-4f3b-bd0a-3f96711e004d"}
      ]
    }
  ],
  "pagination": {
    "count": 1,
    "total": 7,
    "next_marker": "SYS.ECS.cpu_util.instance_id:d9112af5-...",
    "has_more": true
  }
}
```

字段说明:
- `metrics[].unit` 始终返回
- `next_marker` 是华为云的 marker (不是数字 offset), 透传, 下一页直接传回 `start` 参数
- `has_more` 等价于 `next_marker != null && count > 0`, 为客户端提供易用判断

### 3.4 输出契约 (失败)

返回 MCP tool error, 统一结构:

```json
{
  "error_code": "INVALID_PARAM | UPSTREAM_THROTTLED | UPSTREAM_AUTH_FAILED | UPSTREAM_ERROR | TIMEOUT | INTERNAL",
  "error_message": "human readable",
  "upstream_trace_id": "华为云返回的 X-Request-Id, 可用于工单",
  "retryable": true | false
}
```

错误码映射:

| 上游情况 | error_code | retryable |
|---|---|---|
| 输入校验失败 | INVALID_PARAM | false |
| HTTP 429 / SDK throttling | UPSTREAM_THROTTLED | true |
| HTTP 401/403 | UPSTREAM_AUTH_FAILED | false |
| HTTP 5xx | UPSTREAM_ERROR | true |
| 调用超时 | TIMEOUT | true |
| 序列化 / 未分类异常 | INTERNAL | false |

## 4. 与华为云 SDK 的映射

- **SDK 类**: `com.huaweicloud.sdk.ces.v1.CesClient`
- **SDK 方法**: `listMetrics(ListMetricsRequest)`
- **SDK 版本**: v3.1.196

**字段映射**:

| MCP 输入 | SDK Request 字段 |
|---|---|
| `namespace` | `withNamespace(String)` |
| `metric_name` | `withMetricName(String)` |
| `dim_name + dim_value` | `withDim0(String)`, 格式 `"{dim_name},{dim_value}"` |
| `limit` | `withLimit(Integer)` |
| `start` | `withStart(String)` |
| `order` | `withOrder(String)` |

**AI 容易写错的点 (实现时务必注意)**:
1. `dim.0` 在 SDK 里是 `Dim0` 字段, 单字符串拼接 `"key,value"`, 逗号分隔, 不是对象
2. 华为云 ListMetrics 只支持 `dim.0` 一个维度过滤入参 (多维度查询是结果里可能返回多维度, 过滤只能给一个)
3. `start` 是 marker 字符串, 不是 offset 数字
4. SDK 内部可能也对 namespace 做正则校验, 但我们要在调 SDK 前自己拦下来给清晰错误码

## 5. 非功能要求

- **限流**: `ces.listMetrics` 走 Resilience4j RateLimiter, key = `ces-readonly`, 默认 10 QPS, 可配置
- **重试**: 仅对 `UPSTREAM_THROTTLED` / `UPSTREAM_ERROR` / `TIMEOUT` 重试, 最多 3 次, 指数退避 200ms / 800ms / 3.2s
- **超时**: 单次 SDK 调用 10s
- **可观测**:
  - Micrometer 指标: `mcp_tool_invocation{tool="list_ces_metrics", result="success|error", error_code="..."}`
  - 日志 INFO: 入参摘要 + 耗时 + 结果数 + upstream trace_id

## 6. 测试策略 (Definition of Done)

### 单元测试 (mock 华为云 SDK)

| ID | 用例 | 期望 |
|---|---|---|
| UT-01 | 全默认参数 | SDK 被调用, namespace/metric_name 等为 null, limit=100, order=desc |
| UT-02 | 提供 namespace=SYS.ECS | SDK Request.namespace = "SYS.ECS" |
| UT-03 | 提供 dim_name=instance_id + dim_value=xxx | SDK Request.dim0 = "instance_id,xxx" |
| UT-04 | 只提供 dim_name, 不提供 dim_value | 返回 INVALID_PARAM, 不调 SDK |
| UT-05 | limit = 1001 | 返回 INVALID_PARAM, 不调 SDK |
| UT-06 | limit = 0 | 返回 INVALID_PARAM |
| UT-07 | namespace = "syc.ecs" (小写开头) | 返回 INVALID_PARAM |
| UT-08 | order = "random" | 返回 INVALID_PARAM |
| UT-09 | SDK 返回空列表 | 返回 metrics=[], has_more=false, total=0 |
| UT-10 | SDK 返回 100 条 + marker | has_more=true, next_marker 透传 |
| UT-11 | SDK 抛 429 异常 | 触发重试; 3 次后失败返回 UPSTREAM_THROTTLED, retryable=true |
| UT-12 | SDK 抛 401 异常 | 不重试, 返回 UPSTREAM_AUTH_FAILED, retryable=false |
| UT-13 | SDK 抛 5xx | 重试 3 次后失败 UPSTREAM_ERROR |
| UT-14 | SDK 调用超时 | 返回 TIMEOUT |
| UT-15 | 输出始终包含 unit 字段 | 校验所有成功用例的输出含 unit |

### 类型契约测试 (反射 + 样例 JSON)

| ID | 用例 | 期望 |
|---|---|---|
| TC-01 | SDK `MetricInfoList` 类的字段反射 | 包含 namespace / metricName / unit / dimensions 字段 |
| TC-02 | SDK `MetricsDimension` 类反射 | 包含 name / value 字段 |
| TC-03 | SDK `MetaData` 类反射 | 包含 count / marker / total 字段 |
| TC-04 | 自定义 DTO 反序列化 SDK 返回的样例 JSON (来自华为云文档) | 各字段非 null |

类型契约测试的作用: 华为云 SDK 升版本时如果字段改名 / 删字段, 编译期可能发现不了 (Builder 模式 + 反射场景), TC 会在 CI 暴露。

### 录制回放 (预留, 本期不强制)

`mvn test -Precord` profile 预留, 将来如有跳板机或临时联网条件时, 跑此 profile 真实打 CES 并录制响应到 `smartom-contract-tests/src/test/resources/cassettes/list_ces_metrics/*.json`。

### 部署后冒烟 (贵阳环境, 本期必做)

`scripts/smoke/smoke-list_ces_metrics.sh`:

1. 调 tool with `namespace=SYS.ECS, limit=5`, 断言返回 metrics 非空 + 字段齐全
2. 调 tool with `namespace=SYS.NONEXISTENT`, 断言返回空列表 (不是异常)
3. 调 tool with `limit=1001`, 断言返回 INVALID_PARAM

启动时校验: 若 AK/SK 缺失或格式错, Spring Boot 启动 fail-fast, 不进入 ready 状态。

## 7. 验收标准 (DoD)

- [ ] 所有 UT 用例通过 (15 条)
- [ ] 所有 TC 用例通过 (4 条)
- [ ] 在 `smartom-mcp-server` 中通过 MCP Inspector 工具能看到 `list_ces_metrics`, description 正确
- [ ] 配置文件能配 `ces-readonly` RateLimiter 的 QPS
- [ ] 日志含入参摘要 / 耗时 / upstream trace id
- [ ] Micrometer 指标 `mcp_tool_invocation` 可在 actuator/prometheus endpoint 看到
- [ ] 贵阳环境 3 条冒烟脚本全部通过
- [ ] README 包含该 tool 的使用示例
