> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T20`，状态 Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-apm）

- [x] 1.1 新增 `ApmAlarmAdapter` 接口 + `ApmAlarmAdapterImpl`（仅 `listAlarmData`，复用既有 `apmClient` 与 x-business-id 头注入）
- [x] 1.2 新增 DTO record：`ApmAlarmDataRequest`（15 字段）/ `ApmAlarmDataResponse` / `ApmAlarm`（27 字段无损投影）
- [x] 1.3 SDK 异常 → `ErrorCode` 映射（429/401/5xx/Timeout）

## 2. Service 层（agentic-monitoring）

- [x] 2.1 新增 `ApmAlarmService.listAlarmData`，校验 page≥1 / page_size∈[1,100] / business_id 解析
- [x] 2.2 业务异常 `InvalidParamException` → `INVALID_PARAM`

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `ApmAlarmDataTool`，`@Tool(name="list_apm_alarm_data")`，暴露 17 个 `@ToolParam`
- [x] 3.2 `McpServerConfig` 注册（紧跟 `apmTopologyTool`）

## 4. 测试

- [x] 4.1 `ApmAlarmDataToolTest`（UT-T1~T3）
- [x] 4.2 `ApmAlarmServiceTest`（UT-S1~S5）
- [x] 4.3 `ApmAlarmAdapterImplTest`（UT-A1~A2 + 4 异常映射）
- [x] 4.4 `ApmListAlarmDataContractTest`（TC-01，27 字段全断言）+ 样本 `sdk-samples/apm/list-alarm-data-response.json`

## 5. 遗留项（本期未交付）

- [ ] 5.1 冒烟脚本 `scripts/smoke/smoke-list_apm_alarm_data.sh`
- [ ] 5.2 Micrometer 指标看板配置
- [ ] 5.3 README 使用示例
