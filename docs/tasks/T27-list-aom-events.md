# T27 — list_aom_events：AOM 事件/告警查询工具

> 状态: **Done** · 估时: 0.5d · 依赖: spec `docs/specs/tools/list_aom_events.md`、T25（ToolCallSupport）、§4.1/§4.3 · 关联: T26（AOM 枚举卡，未开工，本卡不依赖）

## 背景

诊断 Agent 目前只能查 CES 告警历史（`list_alarms`）与 APM 告警（`list_apm_alarm_data`），
缺 AOM 侧的事件/告警入口。AOM v2 `ListEvents` 一个接口同时覆盖活动告警、历史告警与全部事件
（query 参数 `type` 区分），是告警下钻链（events → metadata → metrics/logs）的起点。

## 范围

**做**：
1. spec：`docs/specs/tools/list_aom_events.md`（已完成，字段表来自 SDK sources jar 逐字段核对）。
2. DTO（aom dto 包）：
   - 请求：`AomListEventsRequest`、`AomEventMetadataRelation`、`AomAlertType`（受控枚举：
     `ACTIVE_ALERT("active_alert")` / `HISTORY_ALERT("history_alert")`，严格 fromValue + 双写法，T24 约定）
   - 响应（无损）：`AomListEventsResponse`、`AomEvent`（11 字段全量）、`AomEventPageInfo`
     （**不要复用 `AomPagination`**——那是 offset 制，本接口是 marker 制 `PageInfo`）
3. `AomPatterns` 增加 `TIME_RANGE` 正则（从 `AomMetricDataService` 的私有常量上提共用，该 service 同步改引用）。
4. 新增 `AomEventAdapter` 接口 + `AomEventAdapterImpl`（`aom-readonly` 限流、API 名 `aom.listEvents`、
   finally 记录 upstreamTraceId 由 `HuaweiCloudInvocation` 统一承担）。
5. 新增 `AomEventService`（校验规则见 spec §4）。
6. 新增 `AomEventTool#list_aom_events` + 注册进 `McpServerConfig`。
7. 测试：spec §5 全部用例（service UT / tool UT / 契约 TC + 样本 JSON / 枚举测试）。

**不做**：
- ❌ 不透传 `Enterprise-Project-Id`（理由见 spec §2）
- ❌ 不做 `CountEvents` / 告警规则 CRUD（写操作，超出只读范围）
- ❌ 不缓存（告警是实时数据，与 T23/T24 的"发现工具才缓存"原则一致）
- ❌ 不动既有 AOM 三工具（T26 范围，另卡）

## 验收标准

- [x] `AomEvent` 覆盖 SDK `ListEventModel` 全部 11 字段；契约测试断言全字段，漂移即 fail
- [x] `type` 为受控枚举，JSON Schema 可见 2 个取值；非法值被拒绝
- [x] spec §4 全部校验路径返回 `INVALID_PARAM`
- [x] mcp 不直接 import huaweicloud SDK；依赖方向不破
- [x] 迭代 `mvn -o -q -pl agentic-mcp -am test`；收尾全量 `mvn verify` 一次通过；Checkstyle 0

## AI 易错点提醒

1. `PageInfo`（marker 制）≠ `ListMetricItems` 的分页（offset 制），别复用 `AomPagination`。
2. `metadata`/`annotations`/`attach_rule`/`policy` 是 Map 类型，照 SDK 原样承载，别拍平。
3. `type` 是 **query 参数**不是 body 字段；`time_range` 等在 body（`EventQueryParam2`）。
4. sort 约束：`order` 提供时 `order_by` 必填（SDK Javadoc 明示），校验放 service。
5. 限流实例用既有 `aom-readonly`，别新建。

## 完成后

PR：`feat(T27): list_aom_events MCP tool — AOM active/history alarm query with lossless event DTO`
