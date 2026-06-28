> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T22-show-apm-trend.md`，状态 Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-apm）

- [x] 1.1 新增 `ApmTrendAdapter` 接口 + `ApmTrendAdapterImpl`（仅 `showTrend`，复用既有 `apmClient` 与 x-business-id 头注入）
- [x] 1.2 新增 6 个 DTO record：`ApmTrendRequest`（businessId + viewConfig + 5 outer）/ `ApmTrendViewConfig`（12 字段，含 `fieldItemList`）/ `ApmTrendFieldItem`（7 字段）/ `ApmTrendResponse`（lineList + latestDataTime）/ `ApmTrendLine`（6 字段，含 `pointList`）/ `ApmTrendPoint`（time: Long, value: Object）
- [x] 1.3 `view_type` / `table_direction` 经 `TrendView.*Enum.fromValue` 映射，`IllegalArgumentException` 兜底包成 `InvalidParamException`
- [x] 1.4 `latest_data_Time` 经 `getLatestDataTime()` 取值，DTO 输出 `latest_data_time`；`fieldItemList` null 透传
- [x] 1.5 SDK 异常 → `ErrorCode` 映射（429/401/5xx/Timeout）

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `ApmTrendService.showTrend`，校验 view_config 非 null / view_type ∈ {trend,sumtable,rawtable} / metric_set 非空 / start_time、end_time 非空 / business_id 解析回落
- [x] 2.2 业务异常 `InvalidParamException` → `INVALID_PARAM`

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `ApmTrendTool`，`@Tool(name="show_apm_trend")`，暴露 5 外层 `@ToolParam` + 嵌套 `view_config`
- [x] 3.2 `McpServerConfig` 注册（紧跟 `apmAlarmNotifyTool`）

## 4. 测试

- [x] 4.1 `ApmTrendToolTest`（UT-T1~T3）
- [x] 4.2 `ApmTrendServiceTest`（UT-S1~S6）
- [x] 4.3 `ApmTrendAdapterImplTest`（UT-A1~A2 + 4 异常映射）
- [x] 4.4 `ApmShowTrendContractTest`（TC-01，lineList/pointList/latestDataTime 全字段断言，value 覆盖 Number + String）+ 样本 `sdk-samples/apm/show-trend-response.json`

## 5. 遗留项（本期未交付）

- [ ] 5.1 冒烟脚本 `scripts/smoke/smoke-show_apm_trend.sh`
- [ ] 5.2 Micrometer 指标看板配置
- [ ] 5.3 README 使用示例
