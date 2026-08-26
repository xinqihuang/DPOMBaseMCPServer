# APM 告警 16557989 诊断报告

- 诊断时间：2026-08-25（只读，通过本地 DPOMBase MCP 调用华为云 APM）
- 告警窗口：2026-08-15 11:56:55–12:01:55（UTC+8）
- 对象：`DPBinMedService/china2023-dpframework`，实例 `2121291`，IP `10.105.49.3`
- 结论：**不是 CodeCache 空间不足，而是告警模板把 Par Eden Space 的指标误标成 CodeCache。**

## 1. 决定性证据

| 对比项 | 告警内容 | APM memoryPool 实测 | 结论 |
|---|---:|---:|---|
| `used`（11:58） | 4,874,852,480 B = 4649.021606 MiB | Par Eden Space = 4649.021606 MiB | 字节级完全一致 |
| `max` | 5,154,013,184 B = 4915.25 MiB | Par Eden Space = 4915.25 MiB | 字节级完全一致 |
| `init` | 5,154,013,184 B = 4915.25 MiB | Par Eden Space = 4915.25 MiB | 字节级完全一致 |
| 告警比值 | 94.5836% | Eden `used/max` = 94.5836% | 超过规则阈值 90% |
| 真实 Code Cache | 告警声称空间不足 | 90.3693/256 MiB = 35.3005% | 未接近阈值且全窗口稳定 |

规则表达式为 `used/max > 0.9`，但规则没有体现内存池 `name=Code Cache` 的有效约束。规则名还残留
`DPModelProxyService`，实际对象却是 `DPBinMedService`，进一步证明这是模板身份和指标语义错配。

## 2. 时间线

| 时间（UTC+8） | Par Eden Space used | 说明 |
|---|---:|---|
| 11:55 | 4407.09 MiB | 逐步填充 |
| 11:56:55 | — | 首次触发告警 |
| 11:58 | 4649.021606 MiB | 与告警 `used` 精确一致，94.58% |
| 12:00 | 4820.20 MiB | 持续填充 |
| 12:01 | 4886.61 MiB | 达 99.42% |
| 12:01:55 | — | APM 历史记录状态变为 `RECOVER` |
| 12:02 | 103.20 MiB | Eden 骤降，符合 minor GC 回收形态 |

同一窗口中：

- Code Cache 始终为 90.3693/256 MiB（35.30%），无尖峰。
- CMS Old Gen 约 853.95/15360 MiB（5.56%），基本平稳。

## 3. 查询链与身份核对

1. `show_env_monitor_items(envId=1306682)` 确认 `monitorItemId=67587` 对应环境内的
   `collectorId=18`、`collectorName=JVM`，采集周期 60 秒。
2. `show_apm_monitor_item_view_config(collectorId=18, envId=1306682)` 返回真实
   `memoryPool` 视图，按 `name` 分组，字段为 `max/used/init/committed`。
3. `show_apm_trend` 返回目标实例 11:40–12:15 的各 JVM 内存池分钟级曲线，完成上面的字节级匹配。
4. `list_apm_alarm_data(businessId=111092, ipAddress=10.105.49.3, 11:40–12:15)` 回查到：
   `regionAlarmEventId=16557989`、`templateId=8465`、`alarmRuleId=17680`、状态 `RECOVER`、
   首次 11:56:55、末次 12:01:55。

用户提供的事件快照显示 `status=ALERT`、末次时间 11:58:55；APM 历史回查显示同一事件后来持续到
12:01:55 并恢复。这是告警生命周期更新，不是两条互相冲突的告警。

## 4. 根因与置信度

**根因置信度：高（约 95%）。** `used/max/init` 三个值均与 Par Eden Space 精确匹配，真实 Code Cache
只有 35.30%，且告警恢复与 Eden 的回收点严格对齐。当前证据足以排除 CodeCache 容量告警。

尚未直接读取模板编辑页，因此不能仅凭趋势数据区分以下两种模板内部实现错误：

- 模板未加 `memoryPool.name=Code Cache` 过滤，取到了 Eden 序列；或
- 模板字段直接错误绑定到 Eden，却沿用了 CodeCache 的规则名称。

## 5. 处置建议

1. 本事件无需按 CodeCache 耗尽处置；不要为此盲目扩大 `ReservedCodeCacheSize` 或重启服务。
2. 检查 APM 模板 `8465` / 规则 `17680` 的内存池过滤条件，确保指标限定到真实 Code Cache 池。
3. 清理规则名中的 `DPModelProxyService` 残留，改为与模板实际作用范围一致的通用名称。
4. 若确实要监控 Eden，不应使用瞬时 `used/max > 90%` 作为严重告警；优先结合持续时长、minor GC
   频率、GC 停顿和晋升速率，避免把正常的“填充后回收”当成故障。

## 6. 查询限制

- 带 `region + monitorItemId + IP + env + 时间窗` 的组合查询被上游拒绝为
  `UPSTREAM_INVALID_PARAM`；缩小为 `businessId + IP + 时间窗` 后成功命中目标告警。
- 本次未执行任何华为云写操作，也未修改告警模板或业务实例。
