# T10 — 实现 query_traces（APM 调用链搜索）

> 状态: **Done**（提交 `4c346d6`，2026-06-02 回填文档） · 估时: 0.5d · 依赖: T03（common 错误码 / 限流基座）· 关联 spec: `docs/specs/tools/query_traces.md`

## 目标

引入 APM adapter 子模块（agentic-adapter-apm）的首个 SDK 能力——`ShowSpanSearch`，提供 MCP tool `query_traces` 让 Agent 按多维条件搜索 APM span。

## 范围

**做**:
- 新增 `ApmTraceAdapter` 接口 + `ApmTraceAdapterImpl`（包含 `queryTraces` 方法）
- 新增 DTO：`ApmQueryTracesRequest` / `ApmQueryTracesResponse` / `ApmSpan`
- 新增 `ApmTraceService`：page / pageSize / timeUsedMin 校验
- MCP tool `ApmTraceTool`，`@Tool(name = "query_traces")` 注册
- `application.yml` 新增 `apm-readonly` RateLimiter 实例（与 ces-readonly 同档：10 QPS）
- `HuaweiCloudProperties` 暴露 `apmBusinessId` / `apmRegion` 配置项
- `ApmClient` Spring bean 装配
- `McpServerConfig` 注入 `ApmTraceTool` 到 `ToolCallbackProvider`

**不做**（防止任务蔓延）:
- ❌ Tool / Service / Adapter 层 UT、Contract Test、冒烟脚本（spec §6 列出，本期未交付，进遗留项）
- ❌ trace 详情下钻（用 `get_service_topology`）
- ❌ APM 日志 / 异常详情

## 前置阅读

**必读**:
1. `docs/specs/tools/query_traces.md` — 完整 spec
2. `CLAUDE.md` §3 / §4 — 编码规范、SDK 不泄漏、错误码统一
3. APM API：https://support.huaweicloud.com/intl/en-us/api-apm2/

## 实际产物清单

```
docs/specs/tools/
  query_traces.md                                     ← 本任务回填（v1.0）
docs/tasks/
  T10-query-apm-traces.md                             ← 本任务卡

agentic-adapter/agentic-adapter-apm/
  pom.xml                                             ← 引入 huaweicloud-sdk-apm
  src/main/java/com/huawei/smartom/agentic/adapter/apm/
    ApmTraceAdapter.java                              ← 新增接口
    ApmTraceAdapterImpl.java                          ← 新增实现（含 queryTraces）
    config/ApmClientConfig.java                       ← ApmClient Spring 装配
    dto/
      ApmQueryTracesRequest.java                      ← 新增 record
      ApmQueryTracesResponse.java                     ← 新增 record
      ApmSpan.java                                    ← 新增 record

agentic-monitoring/
  src/main/java/com/huawei/smartom/agentic/monitoring/apm/
    ApmTraceService.java                              ← 新增（queryTraces 校验 + 委托）

agentic-mcp/
  src/main/java/com/huawei/smartom/agentic/mcp/
    tool/ApmTraceTool.java                            ← 新增（@Tool 注册）
    config/McpServerConfig.java                       ← 注入 ApmTraceTool
  src/main/resources/
    application.yml                                   ← 新增 apm-readonly RateLimiter

agentic-common/
  src/main/java/com/huawei/smartom/agentic/common/config/
    HuaweiCloudProperties.java                        ← 新增 apmBusinessId / apmRegion 字段
```

## 关键技术要求

### 1. APM 鉴权与上下文

- AK/SK 复用 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK`（Vault 注入），与 CES / AOM 一致
- `apmRegion` / `apmBusinessId` 通过 `HuaweiCloudProperties` 配置项注入；`businessId` 入参为 null 时走配置默认值（**fallback 在 adapter 层做**）

### 2. SDK 调用要点

```java
TraceSearchParam body = new TraceSearchParam()
    .withRegion(properties.getApmRegion())   // 必填
    .withPage(request.page())
    .withPageSize(request.pageSize());
// ... setStartTimeString / setSource / setHasError / setTimeUsedMin
ShowSpanSearchRequest sdkRequest = new ShowSpanSearchRequest().withBody(body);
if (businessId != null) {
    sdkRequest.setXBusinessId(businessId);   // HTTP 请求头，不是 body
}
```

### 3. Service 层校验

- `page >= 1`
- `pageSize ∈ [1, 500]`
- `timeUsedMin >= 0`（如提供）
- 其余字段透传给上游

### 4. DTO 兜底

`ApmSpan.tags` 始终非 null（adapter 用 `Map.of()` 兜底），便于 Agent 端直接 `Object.keys(tags)` 不需要判空。

### 5. RateLimiter

新增独立 `apm-readonly` 实例（不复用 `ces-readonly`），避免 CES / APM 互相挤占配额。

## 验收标准

实际完成项（spec §7 mapping）：

- [x] MCP Inspector 能看到 `query_traces`，description 正确
- [x] `apm-readonly` RateLimiter 已配置（`application.yml`）
- [x] 日志含入参摘要（`apm.showSpanSearch start` INFO）
- [x] Checkstyle 0 violations
- [x] 代码已合入 master（`4c346d6`）
- [ ] Tool / Service / Adapter UT（后续任务补）
- [ ] Contract Test（后续任务补）
- [ ] 贵阳冒烟脚本（后续任务补）
- [ ] Micrometer 指标 + README 示例（后续任务补）

## AI 易错点提醒

**spec §4 已列出**：
1. `businessId` 是 HTTP 头 `x-business-id`，用 `setXBusinessId(Long)`，不在 body
2. `region` 必须显式 `withRegion(...)` 写到 body
3. 时间字段是字符串 `yyyy-MM-dd HH:mm:ss`（CES / APM 不同，别混）
4. `ClientSpanInfo.getTags()` 可能为 null，需 `Map.of()` 兜底
5. 响应字段是 `getSpanInfoList() + getTotal()`，不是 `getSpans()`
6. `businessId` 缺失时 fallback 走配置默认值，**在 adapter 层做**

**额外**：
7. **Tool 名是 `query_traces` 而不是 `query_apm_traces`**——任务卡文件名为 `T10-query-apm-traces.md`，但 `@Tool(name=...)` 是 `query_traces`，spec 文件名跟 tool name 走
8. **APM SDK 客户端是 `ApmClient`，不是 `ApmAsyncClient`**（注意区分；后者用于响应式场景，本项目禁用 WebFlux）
9. **`apm-readonly` 与 `ces-readonly` 是两个独立 instance**，不要图省事复用——T03 已建立的限流隔离原则

## 完成后

PR：`feat(T10): add query_traces APM trace search tool`（已提交：`4c346d6`）。
