# T30 — 实现 diagnose_trace（trace 一站式故障诊断编排）

> 状态: **Done** · 估时: 1d · 依赖: T10（query_traces / topology）、T29（trace 诊断链 show_trace_events / show_event_detail / show_clob_detail）· 关联 spec: `docs/specs/tools/diagnose_trace.md`

## 目标

为智能运维 Agent 提供 `diagnose_trace` 编排工具：仅凭一个 `trace_id` 一步完成"拓扑 + 调用链事件 + 出错/最慢 span 详情 + clob 全文"的下钻，直接产出聚焦根因的诊断包，免去手工串联四步。

## 范围

**做**:
1. monitoring 层新增 `DiagnoseTraceService`（编排既有 `ApmTraceService` 的 getTopology / showTraceEvents / showEventDetail / showClobDetail；虚拟线程并发；阶段独立错误）
2. 新增 DTO（record）：`DiagnoseTraceRequest` / `DiagnoseTraceResponse` / `SuspectEvent` / `ResolvedClob` / `DiagnoseStageError`
3. mcp 层新增 `DiagnoseTraceTool`，`@Tool(name="diagnose_trace")`，实现 `McpTool` 自动注册（4 个 `@ToolParam`）
4. 测试：`DiagnoseTraceServiceTest`（UT-S1~S5）、`DiagnoseTraceToolTest`（UT-T1~T4）

**不做**（防蔓延）:
- ❌ 新增华为云 SDK 调用 / 新 adapter 方法（纯编排，复用既有 service）
- ❌ ContractTest（无新 SDK 响应映射，与 correlate_incident 一致）
- ❌ 改既有 APM 工具 / RateLimiter（复用 `apm-readonly`）
- ❌ 冒烟脚本 / Micrometer 看板（进遗留项）

## 前置阅读

1. `docs/specs/tools/diagnose_trace.md` — 完整 spec
2. `agentic-monitoring/.../correlate/CorrelateIncidentService.java` — 同类编排器范式（虚拟线程 + 三态分支）
3. `CLAUDE.md` §4.3 / §4.4 — 面向 Agent 入参不臆造、并发模型

## 产物清单

```
docs/specs/tools/diagnose_trace.md                                  ← 本任务
docs/tasks/T30-diagnose-trace.md                                    ← 本任务卡

agentic-monitoring/src/main/java/com/huawei/smartom/agentic/monitoring/apm/
  DiagnoseTraceRequest.java                                         ← 新增 record
  DiagnoseTraceResponse.java                                        ← 新增 record
  SuspectEvent.java                                                 ← 新增 record
  ResolvedClob.java                                                 ← 新增 record
  DiagnoseStageError.java                                           ← 新增 record
  DiagnoseTraceService.java                                         ← 新增编排服务
agentic-monitoring/src/test/java/com/huawei/smartom/agentic/monitoring/apm/
  DiagnoseTraceServiceTest.java                                     ← 新增（UT-S1~S5）

agentic-mcp/src/main/java/com/huawei/smartom/agentic/mcp/tool/
  DiagnoseTraceTool.java                                            ← 新增工具
agentic-mcp/src/test/java/com/huawei/smartom/agentic/mcp/tool/
  DiagnoseTraceToolTest.java                                        ← 新增（UT-T1~T4）
```

## 关键技术要求

1. **clob 识别**: event 详情 `tags` 中 key 以 `_clob_id` 结尾（`stacktrace_clob_id` / `sql_clob_id`），value 即 `clob_id`。
2. **可疑 span 挑选**: 出错优先（`has_error=true`），再按 `time_used` 倒序补最慢，`LinkedHashMap` 去重保序，截断到 `max_suspect_events`（默认 5）。
3. **event_id 兜底**: `ApmSpanEvent` 同时有 `event_id` 与 `id`，优先 `event_id`，为空取 `id`。
4. **阶段独立**: 每阶段 catch `SmartomException` → 记 `DiagnoseStageError(stage, code, retryable, msg, upstreamTraceId)`，对应字段置空，不抛出。
5. **并发**: topology 与 events 并发；可疑 span 的 detail+clob 并发；统一虚拟线程 executor，try-with-resources 管理。
6. **方法体 ≤50 行**: 编排主流程拆 `assemble` / `selectSuspects` / `deepDive` / `buildSuspect` / `fetchDetail` / `resolveClobs` / `fetchClob`。

## 验收标准

- [x] `mvn -pl agentic-monitoring,agentic-mcp -am test -Dtest=DiagnoseTraceServiceTest,DiagnoseTraceToolTest` 全绿（9 条）
- [x] Checkstyle 0（两模块 `checkstyle:check` BUILD SUCCESS）
- [x] 自动注册（`McpTool` 标记），MCP 工具总数 +1
- [x] spec 状态 Approved，T30 状态 Done
- [ ] 冒烟脚本 / Micrometer 看板（遗留）

## AI 易错点提醒

1. **clob 引用来自详情的 tags，不是原始事件的 tags**——`resolveClobs` 用 `showEventDetail` 返回的 `event_info.tags`。
2. **`ApmSpanEvent` 字段多（40），测试用 Jackson 从小 JSON 构造**，别手写巨型构造器。
3. **方法/catch 参数名 ≥2 字符**（checkstyle `ParameterName`）——别用单字符 `e` 作方法参数。
4. **纯编排不碰 SDK**——依赖 `ApmTraceService` 而非 adapter，遵守依赖方向。

## 完成后

PR 标题：`feat(T30): diagnose_trace orchestration tool for one-shot trace fault diagnosis`。
遗留项：冒烟脚本 / Micrometer 看板；后续可在 OpenSpec 迁移中补 `diagnose-trace` capability change。
