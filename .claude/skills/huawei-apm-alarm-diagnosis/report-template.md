# APM 告警诊断报告模板

诊断完成后按此结构输出。每条结论后用 `[来源: <工具名> 字段]` 标明证据出处，无证据不下结论。

---

## 1. 告警摘要
- 告警 ID / 规则名 / 严重度：
- 应用 (app_name) / 环境 (env_id) / 实例 IP / region：
- 触发与持续：`alarm_first_time` ~ `alarm_last_time`，状态：
- 告警表达式：
- 投递情况（可选）：渠道 / 成功失败 `[来源: list_alarm_notify]`

## 2. 异常确认
- 指标趋势：劣化起始时刻、峰值/谷值量级、是否仍在持续 `[来源: show_apm_trend line_list]`
- 监控项 → collector：`monitor_item_id` / `collector_id`（env 局部）

## 3. 故障定位
- 受影响服务节点 / 调用边：`[来源: get_service_topology]`
- 代表性 trace_id：`[来源: query_traces]`
- 内部可疑步骤（方法 / SQL / 远程调用 + 耗时 / 状态码）：`[来源: show_trace_events]`

## 4. 根因
- 根因类型：异常 / 慢 SQL / 超时 / 限流 / 资源饱和 / 下游故障 …
- 关键证据：异常类与消息 / SQL / HTTP 状态 / 完整堆栈 `[来源: show_event_detail / show_clob_detail]`

## 5. 关联信号（按需）
- CES 基础设施告警：`[来源: list_alarms]`
- 应用日志佐证：`[来源: query_logs / query_lts_logs]`
- 资源饱和度（CPU/内存/连接）：`[来源: query_aom_metric_data]`
- 横切快照：`[来源: correlate_incident]`（注明各分支 ok / skipped / failed）

## 6. 结论与建议动作
- 一句话根因：
- 建议动作（只读诊断，不代为执行写操作）：
- 不确定项 / 未覆盖项 / 失败分支：如实列出
