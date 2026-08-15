# Architecture — DPOMBaseMCPServer

> 系统级架构基线。新人 / AI 在编码前应先读完本文档。

## 1. 一句话定位

把华为云 AOM/APM/CES/LTS 的只读监控 API，包装成 MCP Server，提供给智能运维 Agent 调用。

生产默认工具面严格只读。历史 CES 通知屏蔽写工具仅用于隔离的人工运维场景，必须同时启用
`action-enabled` profile 与 `dpom.mcp.write-tools-enabled=true` 才会注册；DPOMAgent 不得启用或调用它们。

## 2. 上下文图

```
┌──────────────────────┐         MCP / SSE          ┌────────────────────────┐
│  智能运维 Agent       │ ◄─────────────────────────► │  DPOMBaseMCPServer     │
│  (Claude/通义/...)   │     list_ces_metrics ...   │  (Spring Boot 3.4)     │
└──────────────────────┘                            └────────────┬───────────┘
                                                                 │
                                                  Huawei Cloud Java SDK 3.1.177
                                                                 │
                       ┌───────────────────────┬─────────────────┴─────────────┐
                       ▼                       ▼                               ▼
                 ┌──────────┐            ┌──────────┐                   ┌──────────┐
                 │ CES API  │            │ AOM API  │                   │ APM API  │
                 │ (云监控)  │            │ (应用运维)│                   │ (调用链)  │
                 └──────────┘            └──────────┘                   └──────────┘
```

## 3. 内部分层

七个 Maven 模块（含一个聚合父模块），单向依赖：

```
┌─────────────────────────────────────────────────────────┐
│  com.huawei.smartom.agentic.mcp                         │  ← Spring Boot 启动 + MCP tool 注册 + SSE
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  com.huawei.smartom.agentic.monitoring                  │  ← 业务编排 (含 correlate 等高阶 tool)
└──┬───────────────────┬────────────────────────┬─────────┘
   │                   │                        │
┌──▼────────────────────────────────────────────▼─────────┐
│  agentic-adapter  (聚合父 pom，packaging=pom)            │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │ adapter.ces │ │ adapter.aom  │ │ adapter.apm      │  │  ← SDK 封装 + 自定义 DTO
│  └──┬──────────┘ └──┬───────────┘ └──────┬───────────┘  │
└─────┼───────────────┼────────────────────┼──────────────┘
      │               │                    │
      └───────────────┼────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│  com.huawei.smartom.agentic.common                      │  ← 错误码 / 异常 / DTO 基类 / Resilience4j
└─────────────────────────────────────────────────────────┘
```

Maven 模块目录结构：

```
DPOMBaseMCPServer/
├── agentic-common/
├── agentic-adapter/                    ← 聚合父 pom（无源码）
│   ├── agentic-adapter-ces/
│   ├── agentic-adapter-aom/
│   └── agentic-adapter-apm/
├── agentic-monitoring/
└── agentic-mcp/
```

**关键约束**：
- `agentic-mcp` 不直接 import huaweicloud SDK
- `agentic-adapter-ces` / `agentic-adapter-aom` / `agentic-adapter-apm` 之间不能互相依赖
- `agentic-monitoring` 通过 adapter 接口访问下层，不直接 new SDK 类
- 所有跨模块通信用我们自己的 DTO，不传 SDK Request/Response

## 4. 关键运行时特性

### 4.1 并发模型

- JDK 21 虚拟线程开启（`spring.threads.virtual.enabled=true`）
- 华为云 SDK 同步阻塞调用 → 在虚拟线程里直接调，省下线程池管理成本
- 编排层并发用 `CompletableFuture` + 虚拟线程 executor

### 4.2 容错策略

- **限流**：每个 service 一个 RateLimiter（`ces-readonly` / `aom-readonly` / `apm-readonly`），默认 10 QPS
- **重试**：仅对可重试错误（限流 / 5xx / 超时）重试 3 次，指数退避 200/800/3200ms
- **超时**：单次 SDK 调用 10s
- **熔断**：MVP 阶段不做
- **缓存**：MVP 阶段不做

### 4.3 可观测性（吃自己狗粮）

- **Metrics**：Micrometer → AOM Prometheus exporter
  - 核心指标：`mcp_tool_invocation_total{tool,result,error_code}`
  - 核心指标：`mcp_tool_invocation_duration_seconds{tool,result}`
  - 核心指标：`huaweicloud_sdk_call_duration_seconds{service,api,result}`
- **Logs**：Logback JSON → stdout → CCE 日志采集到 AOM
- **Traces**：MVP 不做（避免 MCP → APM → MCP 自身循环采集的复杂度）

### 4.4 配置与凭证

- **配置文件**：`application.yml`（默认）+ `application-<env>.yml`（环境覆盖）
- **AK/SK**：Vault 注入到环境变量 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK`，应用启动时冷加载
- **轮换**：通过 K8s 滚动重启实现，无热更新

## 5. 数据流：以 list_ces_metrics 为例

```
Agent
  │ MCP request: {tool: "list_ces_metrics", args: {namespace: "SYS.ECS", limit: 10}}
  ▼
[mcp] McpToolRegistry
  │ 路由到 CesMetricsTool.listMetrics(args)
  ▼
[monitoring] CesMetricsService
  │ 参数校验 (jakarta validation)
  │ 调 CesMetricsAdapter.listMetrics(CesListMetricsRequest)
  ▼
[adapter.ces] CesMetricsAdapterImpl
  │ ① RateLimiter.acquirePermission("ces-readonly")
  │ ② Retry 包裹下面逻辑
  │ ③ CesListMetricsRequest → SDK ListMetricsRequest
  │ ④ cesClient.listMetrics(sdkRequest)
  │ ⑤ SDK ListMetricsResponse → CesListMetricsResponse (我们的 record)
  │ ⑥ 记录 upstreamTraceId 到日志、metric
  ▼
返回沿原路逐层返回到 Agent
```

错误路径：

```
任意一层抛 Throwable
  ↓
adapter 层 catch 并 wrap 成 SmartomException(errorCode=..., upstreamTraceId=...)
  ↓
monitoring 层透传 (不再包装)
  ↓
mcp 层 catch SmartomException → MCP tool error response
```

## 6. 测试策略

详见 `CLAUDE.md` §5.3。本项目因 CI 网络不通，采用：

1. **单元测试**：完全 mock SDK，覆盖业务逻辑分支
2. **类型契约测试**：反射 + 样例 JSON 校验，防 SDK 升级时静默改字段
3. **录制回放**：预留 `mvn test -Precord` profile，未来若有联网条件可启用
4. **部署后冒烟**：贵阳环境部署后跑 `scripts/smoke/*.sh`

## 7. 部署拓扑

```
CCE 集群 (贵阳一 region)
└── Deployment: dpom-mcp-server
    └── Pod (replicas: 2, 4U8G each)
        ├── Container: dpom-mcp-server
        │   ├── env: HUAWEICLOUD_AK, HUAWEICLOUD_SK (from Vault)
        │   ├── port: 8080 (SSE)
        │   ├── port: 8081 (actuator/management)
        │   └── readinessProbe: /actuator/health/readiness
        └── ConfigMap: application config
```

详见 `helm/dpom-mcp-server/`。
