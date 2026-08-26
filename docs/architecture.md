# Architecture — DPOMBaseMCPServer

> 系统级架构基线。新人 / AI 在编码前应先读完本文档。

## 1. 一句话定位

作为 Phase 1 在线诊断系统记录：把华为云 AOM/APM/CES/LTS/CCE 等只读证据包装为受控端口，
持久化 Investigation Runtime，并向 SRE 发布版本化 Kafka 事件、向 Portal 提供 REST/SSE。

生产默认工具面严格只读。历史 CES 通知屏蔽写工具仅用于隔离的人工运维场景，必须同时启用
`action-enabled` profile 与 `dpom.mcp.write-tools-enabled=true` 才会注册；DPOMAgent 不得启用或调用它们。

## 2. Phase 1B 上下文图

```
┌──────────────────────┐       REST / SSE / MCP     ┌────────────────────────┐
│ Portal / 受控调用方   │ ◄─────────────────────────► │  DPOMBaseMCPServer     │
└──────────────────────┘                            │  Investigation SoR     │
                                                   └──────┬────────┬────────┘
                                                          │        │ Kafka v2/progress
                                          Huawei SDK      │        ▼
                                                          │  SRE Intelligence
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

现有七个 Maven 模块是 Phase 1A 证据能力；Phase 1B 增加 `agentic-diagnosis`、`agentic-persistence`、
`agentic-messaging`，并保持 `agentic-mcp` 为唯一 executable。目标单向依赖为：

```text
agentic-mcp -> agentic-diagnosis <- agentic-persistence
     |                 ^          <- agentic-messaging
     v                 |
agentic-monitoring -> adapter.* -> agentic-common
```

下图保留 Phase 1A 证据分层细节：

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
- `agentic-diagnosis` 不依赖 Spring、MyBatis、Kafka 或 Huawei SDK
- persistence/messaging 只实现 diagnosis 端口，不把 provider/transport DTO 泄漏进领域
- 服务间只通过版本化事件、API 或不可变 Artifact 交互，禁止跨库访问

### 3.1 持久化与发布

- 新生产 schema 由部署流程显式应用 SQL；应用只校验 schema readiness，不在生产自动迁移。
- 领域终态与不可变 publication intent 同事务提交，网络发布在事务外以有界租约重试。
- Kafka 保证为 investigation 分区内的至少一次传输；SRE 以规范身份、摘要、authority epoch 和 sequence 去重。
- 直接 SSE 与 Kafka progress 都来自同一个已持久化 progress log，不携带证据正文或敏感信息。

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

### 4.5 自动 OBS 证据存储

- 诊断证据在 `BoundedEvidenceArtifactStore` 边界完成敏感字段净化、RFC 8785 规范化和 SHA-256 摘要，
  上传成功并校验对象身份后才返回 `EvidenceRecord`；上传失败不得生成悬空引用。
- 每个部署环境通过 `DPOM_OBS_ENDPOINT`、`DPOM_OBS_BUCKET`、`DPOM_OBS_PREFIX` 和
  `DPOM_OBS_SERVICE_CODE` 固定自己的目标。代码、`application.yml` 和 Helm values 不提供测试桶默认值。
- `DPOM_OBS_ENABLED` 与 `DPOM_OBS_AUTOMATIC_STORAGE_ENABLED` 均为 `true` 时才注册自动存储，默认关闭；
  启用后 endpoint、bucket、prefix 或 service code 缺失会让组合根 fail-closed。
- AK/SK 只从 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK` 注入；可选 `DPOM_OBS_KMS_KEY_ID` 未配置时仍请求
  OBS SSE-KMS，由云侧选择默认主密钥。
- 自动写入不设置逐证据包人工审批。安全边界由部署 gate、有界载荷、确定性对象键、敏感字段净化、
  SSE-KMS 和最小权限 IAM 共同承担。运行身份只授予目标 bucket/prefix 所需的 Put/Get/Head 与 KMS 权限。
- 对象键格式为
  `{prefix}/{serviceCode}/{investigationId}/{evidenceType}/{sha256}.json`；测试验证对象使用
  `{prefix}/verification/`，目标同样只能通过进程级运行参数指定。

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
