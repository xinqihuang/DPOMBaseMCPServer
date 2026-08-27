# Architecture — DPOMBaseMCPServer

## 1. 一句话定位

DPOMBaseMCPServer 是无模型、无诊断状态、无业务决策的证据工具服务。它向 DPOMAgent 提供有界、可追溯、
可校验的观测事实；DPOMAgent 独立完成 Investigation、Diagnosis、ToolUse 判断、报告和 Diagnosis Event 发布。

## 2. 服务关系

```text
DPOMAgent（诊断权威 / LLM / ToolUse / Kafka producer）
       |
       | MCP：有界证据请求
       v
DPOMBaseMCPServer（采集 / 标准化 / 证据聚合 / OBS Artifact）
       |
       +--> CES / AOM / APM / LTS 只读 API
       +--> OBS Put / Head / Get（受部署配置约束）

HuaweiCloudAlarmChangeGuard（独立生产变更边界）
       +--> CES / AOM / APM 告警屏蔽、关闭与重新启用
```

DPOMBase 与 SRE Intelligence 之间没有诊断 HTTP Outbox 或 Kafka producer。DPOMAgent 负责向 SRE 发布诊断事件；
DPOMBase 不构造、保存或重放这些消息。

## 3. 内部分层

```text
agentic-mcp
    -> agentic-monitoring
        -> agentic-adapter-{ces,aom,apm,lts,obs}
            -> agentic-common
```

- `agentic-mcp`：Spring Boot composition root、MCP Tool 注册与传输。
- `agentic-monitoring`：输入边界、只读证据编排、确定性跨源聚合、证据包处理。
- `agentic-adapter-*`：华为云 SDK 类型隔离、稳定 DTO、限流/重试/错误映射。
- `agentic-common`：统一错误、配置与共享基础设施。

禁止重新引入 diagnosis、persistence、messaging 模块，禁止依赖 Kafka/MyBatis/模型客户端，禁止扫描父目录或
同级仓库取得测试契约。

## 4. 数据与行为边界

工具响应可以包含观测事实、来源、查询时间窗、上游 request id、完整性摘要和稳定错误；不得包含模型生成的
Hypothesis、Root Cause、Conclusion 或修复决策。`correlate_incident` 只并发汇总各来源分支及其状态，不执行推理。

普通工具必须是只读查询或发现操作。唯一允许的写入是受控证据 Artifact 写入 OBS；它不修改业务资源。
CES/APM 告警规则变更适配器和工具不属于本服务。

## 5. OBS 证据 Artifact

- 目标由 `DPOM_OBS_ENDPOINT`、`DPOM_OBS_BUCKET`、`DPOM_OBS_PREFIX`、`DPOM_OBS_SERVICE_CODE` 按环境配置；
- 对象键为 `{prefix}/{serviceCode}/{collectionId}/{evidenceType}/{sha256}.json`；
- 写入前执行敏感字段净化、RFC 8785 规范化、SHA-256 摘要和大小限制；
- 上传失败不得返回悬空引用；Head/Get 用于对象身份和内容完整性验证；
- `DPOM_OBS_ENABLED` 与 `DPOM_OBS_AUTOMATIC_STORAGE_ENABLED` 控制自动存储，缺少必要配置时 fail-closed；
- 凭据仅从 `HUAWEICLOUD_AK`、`HUAWEICLOUD_SK` 注入，配置与日志不得包含凭据。

## 6. 运行时与容错

- JDK 21 虚拟线程承载同步 SDK 调用；
- 按云服务设置只读 RateLimiter，重试仅覆盖限流、5xx 与超时；
- SDK 异常统一映射到稳定错误码并保留可用的上游 request id；
- 日志不得输出 AK/SK、token 或证据正文；
- SDK Request/Response 类型不得越过 adapter 边界。

## 7. 验证

`mvn clean verify` 必须在干净副本中、没有同级 contracts、Kafka、MySQL 和模型凭证时通过。架构测试维护
显式 MCP Tool allowlist，并阻止诊断模块、Kafka producer、生产告警变更接口和外部契约路径重新进入活动构建。
