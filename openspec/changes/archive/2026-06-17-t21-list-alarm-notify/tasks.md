> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T21-list-alarm-notify.md`，状态 Done；建立在 T20 的 `ApmAlarmAdapter` / `ApmAlarmService` 骨架之上）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. Adapter 层（agentic-adapter-apm）

- [x] 1.1 在 T20 既有 `ApmAlarmAdapter` 接口追加 `listAlarmNotify(ApmAlarmNotifyRequest)` 方法签名
- [x] 1.2 在 `ApmAlarmAdapterImpl` 实现 `listAlarmNotify`：装配 `AlarmNotifyListRequest`（4 字段）、注入 `x-business-id` 头、经 `invocation.execute("apm-readonly", "huaweicloud-retryable", "apm.listAlarmNotify", ...)` 调用 `apmClient.listAlarmNotify`
- [x] 1.3 新增 DTO record：`ApmAlarmNotifyRequest`（5 字段）/ `ApmAlarmNotifyResponse`（`notifications` + `totalCount`）/ `ApmAlarmNotification`（8 字段无损投影）
- [x] 1.4 SDK 异常 → `ErrorCode` 映射（429 / 401 / 5xx / Timeout）复用 T20 路径

## 2. Service 层（agentic-monitoring）

- [x] 2.1 在 T20 既有 `ApmAlarmService` 追加 `listAlarmNotify`，校验 `alarm_data_id` 必填且 > 0、`page ≥ 1`、`page_size ∈ [1,100]`、`business_id` 解析
- [x] 2.2 业务异常 `InvalidParamException` → `INVALID_PARAM`

## 3. MCP 工具层（agentic-mcp）

- [x] 3.1 新增 `ApmAlarmNotifyTool`，`@Tool(name="list_alarm_notify")`，暴露 5 个 `@ToolParam`（business_id / alarm_data_id / page / page_size / region）
- [x] 3.2 `McpServerConfig` 注册（紧跟 `apmAlarmDataTool` 之后）

## 4. 测试

- [x] 4.1 `ApmAlarmNotifyToolTest`（UT-T1~T3：入参装配透传 / `InvalidParamException` → INVALID_PARAM / `UpstreamException` → ErrorResponse 含 trace id）
- [x] 4.2 扩展 `ApmAlarmServiceTest`（UT-S1~S5：alarm_data_id null / ≤0、page_size=0、businessId 与默认均空、全合法委托 adapter）
- [x] 4.3 扩展 `ApmAlarmAdapterImplTest`（UT-A1 SDK 调用映射 + header 注入；UT-A2 429/401/5xx/Timeout 四 case）
- [x] 4.4 `ApmListAlarmNotifyContractTest`（TC-01，8 字段全断言）+ 样本 `sdk-samples/apm/list-alarm-notify-response.json`

## 5. 遗留项（本期未交付）

- [ ] 5.1 冒烟脚本 `scripts/smoke/smoke-list_alarm_notify.sh`
- [ ] 5.2 Micrometer 指标看板配置
- [ ] 5.3 README 使用示例
