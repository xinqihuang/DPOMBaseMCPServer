> 存量回填：以下任务已于早期 commit 交付（原任务卡 T29，状态 Done）。已交付项勾 `[x]`。

## 1. show_trace_events

- [x] 1.1 `ApmTraceAdapter.showTraceEvents` + impl（query `trace_id`，`apm.showTraceEvents`）
- [x] 1.2 DTO：`ApmTraceEventsResponse`、`ApmSpanEvent`（38 字段，含 `next_spanId`）、`ApmDiscardInfo`（含 `totalTime`）
- [x] 1.3 `ApmTraceService.showTraceEvents`（trace_id 校验）
- [x] 1.4 `ApmTraceEventsTool#show_trace_events` 注册

## 2. show_event_detail

- [x] 2.1 `ApmTraceAdapter.showEventDetail` + impl（四 query 参数）
- [x] 2.2 DTO：`ApmEventDetailRequest`、`ApmEventDetailResponse`（`event_info` 复用 `ApmSpanEvent`）
- [x] 2.3 `ApmTraceService.showEventDetail`（四参必填校验）
- [x] 2.4 `ApmEventDetailTool#show_event_detail` 注册

## 3. show_clob_detail

- [x] 3.1 `ApmTraceAdapter.showClobDetail` + impl（header business_id 回落 + body env_id/clob_id）
- [x] 3.2 DTO：`ApmClobDetailRequest`、`ApmClobDetailResponse`（`clob_string`）
- [x] 3.3 `ApmTraceService.showClobDetail`（env_id/clob_id 必填校验）
- [x] 3.4 `ApmClobDetailTool#show_clob_detail` 注册

## 4. 测试

- [x] 4.1 三工具契约 TC（38+3 / event_info / clob_string，含请求侧装配断言）
- [x] 4.2 service UT（各必填校验）、tool UT（透传 + 异常转 ErrorResponse）

## 5. 遗留项（本期未交付）

- [ ] 5.1 三工具的冒烟脚本
- [ ] 5.2 Micrometer 指标看板
