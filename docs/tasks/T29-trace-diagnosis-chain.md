# T29 — show_trace_events + show_event_detail + show_clob_detail：traceId 根因诊断链

> 状态: **Done** · 估时: 0.5–1d · 依赖: spec 三份（show_trace_events / show_event_detail / show_clob_detail）、T10/T11（trace 概要与拓扑）、§4.1/§4.3 · 字段以 SDK sources jar 3.1.177 为准

## 背景

Agent 拿到 trace_id 后目前只能走到「哪条链路、哪个组件有问题」（`query_traces` span 概要 +
`get_service_topology` 拓扑），回答不了「为什么」。补三个工具后 traceId 诊断链自闭环：

```
trace_id（告警/日志/query_traces）
  → show_trace_events（事件序列：哪一步慢/抛错，拿 event_id/span_id/env_id）
    → show_event_detail（事件 tags：异常类名/message/SQL/HTTP 状态）
      → show_clob_detail（完整堆栈/完整 SQL 全文，按 clob_id 取回）
```

## 范围

**做**：
1. spec 三份（已完成）。
2. DTO（apm dto 包，§4.1 无损）：
   - `ApmSpanEvent`（**38 字段全量**，trace-events 与 event-detail 共用；
     注意 SDK 原样混合命名键 `next_spanId`，照抄）
   - `ApmDiscardInfo`（type/count/**totalTime**，驼峰照抄）
   - `ApmTraceEventsResponse`、`ApmEventDetailRequest`、`ApmEventDetailResponse`、
     `ApmClobDetailRequest`、`ApmClobDetailResponse`
3. `ApmTraceAdapter` 增加三方法（同属 trace 域；impl 已有 properties 可做 business 回落）：
   `showTraceEvents(traceId)` / `showEventDetail(req)` / `showClobDetail(req)`；
   API 名 `apm.showTraceEvents` / `apm.showEventDetail` / `apm.showClobDetail`，限流 `apm-readonly`。
4. `ApmTraceService` 增加三方法（校验见各 spec §4；**全部不缓存**——实时数据）。
5. 新增三个工具类 + 注册 `McpServerConfig`；描述写明链路顺序与入参来源（禁止编造）。
6. 测试：三份契约 TC + 样本 JSON、service 校验 UT、tool UT。

**不做**：
- ❌ searchTransaction / showTransactionDetail（URL 事务维度，另一条入口，另卡）
- ❌ showRawTable / showSumTable / showFlameLineTree（指标表格与方法级 profiling，本卡不碰）
- ❌ 不缓存（全链路实时数据）
- ❌ 不动 query_traces / get_service_topology 既有实现

## 验收标准

- [x] `ApmSpanEvent` 覆盖 SDK `SpanEventInfo` 全部 38 字段（含 `next_spanId` 混合命名键、
      discard/attachment/tags 复合结构）；`ApmDiscardInfo` 3 字段；契约测试漂移即 fail
- [x] `show_event_detail` 四 query 参数（trace_id/span_id/event_id/env_id）装配正确且必填校验齐
- [x] `show_clob_detail` header business_id 回落配置默认值；body env_id/clob_id 装配正确
- [x] 三工具描述写明调用顺序与「入参取自前置工具真实响应、禁止编造」
- [x] mcp 不直接 import huaweicloud SDK；依赖方向不破
- [x] 迭代 `mvn -o -q -pl agentic-mcp -am test`；收尾全量 `mvn verify` 一次通过；Checkstyle 0

## AI 易错点提醒

1. `next_spanId` / `totalTime` 是 SDK 原样的混合/驼峰 JSON 键，`@JsonProperty` 照抄，别"规范化"成 snake_case。
2. `ApmSpanEvent` 两个响应共用一个 record，别复制两份；38 字段的映射方法属「机械映射」，
   保持一行一字段且 ≤50 行。
3. `show_event_detail` 的四个参数都是 **query** 位置（SDK meta 已核实），不是 body。
4. `show_clob_detail` 是 **POST + body**（与前两个 GET 不同），别搞混。
5. `tags`/`attachment` 是 Map\<String,String\>，照原样承载，别拍平或挑字段。
6. 与真实 SDK 冲突 → 停下来问（CLAUDE.md §5.1）。

## 完成后

PR：`feat(T29): show_trace_events + show_event_detail + show_clob_detail — traceId root-cause chain`
