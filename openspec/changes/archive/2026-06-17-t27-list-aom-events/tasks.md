> 存量回填：以下任务已于早期 commit 交付（原任务卡 T27，状态 Done）。已交付项勾 `[x]`，遗留项列末尾。

## 1. Adapter 层（agentic-adapter-aom）

- [x] 1.1 新增 `AomEventAdapter` 接口 + `AomEventAdapterImpl`（`aom-readonly` 限流、API 名 `aom.listEvents`）
- [x] 1.2 请求 DTO：`AomListEventsRequest`、`AomEventMetadataRelation`、受控枚举 `AomAlertType`（严格 fromValue + 双写法）
- [x] 1.3 响应 DTO（无损）：`AomListEventsResponse`、`AomEvent`（11 字段）、`AomEventPageInfo`（marker 制，不复用 `AomPagination`）
- [x] 1.4 `AomPatterns.TIME_RANGE` 正则上提共用，`AomMetricDataService` 同步改引用

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `AomEventService`，校验 time_range/step/limit/sort 成对/relation 枚举（spec §4）

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `AomEventTool#list_aom_events` 并注册

## 4. 测试

- [x] 4.1 service UT（校验路径）、tool UT（透传 + 异常转 ErrorResponse + type 枚举映射）
- [x] 4.2 契约 TC：`sdk-samples/aom/list-events-response.json`（全字段 + 最小字段各一条），断言 11+3 字段
- [x] 4.3 `AomAlertType` 枚举封闭性 / 双写法 / 未知值拒绝

## 5. 遗留项（本期未交付）

- [ ] 5.1 冒烟脚本 `scripts/smoke/smoke-list_aom_events.sh`
- [ ] 5.2 Micrometer 指标看板
