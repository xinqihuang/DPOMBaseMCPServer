# APM 告警 16557997 诊断报告 — “CodeCache 空间不足”实为误报（模板指标错配）

- 诊断时间：2026-08-16（只读，经真实 MCP SSE 协议调用）
- 告警对象：region=cn-north-9，businessId=111092（dpframework），envId=2297428，instanceId=2141002，IP=10.99.10.132，appName=DPSouthProxyService/china2023_pvms
- 结论摘要：**并非 CodeCache 真耗尽**。实际是「JVM 堆年轻代（Par Eden Space）」在两次 minor GC 之间自然填充到 97.98% 触发了名为“CodeCache 空间不足”的模板告警；真实 JVM CodeCache 仅约 39/128 MB（≈30.7%），全程平稳。告警已自愈（status=RECOVER）。同时规则名中的「DPModelProxyService」是模板名残留，实际作用在 DPSouthProxyService 等多服务上。

---

## 1. 使用工具与用途

| 工具 | 用途 |
|---|---|
| discover_resource_context | 锚点规范化、provenance/冲突核对、确认目标组件与 missing capability |
| show_env_monitor_items | monitorItemId=121982 → collectorId=18（JVM监控）的权威映射 |
| show_apm_monitor_item_view_config | 获取 JVM 采集器视图的 metric_set/field_item_list（memoryPool 等） |
| show_apm_trend（memoryPool） | 拉取该实例各内存池（含 Code Cache 与 Par Eden Space）指标时间序列 |
| list_apm_alarm_data | 核对告警记录：规则名/表达式/模板 id/作用服务/起止时间/恢复状态 |

未使用：Trace 类工具（无 traceId/接口证据，不盲扫）；LTS 类工具（APM→LTS 映射缺失，见 §4 缺口）。

---

## 2. 时间线（2026-08-15，UTC+8）

| 时间 | 事件 / 观测值 |
|---|---|
| 11:45:00 | Par Eden Space used ≈ 498 MB（约 52%，正常） |
| 11:50:00 | Eden used ≈ 654 MB |
| 11:55:00 | Eden used ≈ 813 MB（逼近 90% 阈值 864 MB） |
| 11:57:00 | Eden used ≈ 874 MB |
| **11:57:46** | **告警首次触发（first）**，规则 used/max > 0.9 |
| 11:58:00 | Eden used ≈ 907 MB |
| 11:59:00 | Eden used ≈ **940.6 MB（=986309632 字节，峰值，97.98%）** |
| **11:59:46** | **告警结束（last，status=RECOVER）** |
| 12:00:00 | **minor GC 触发，Eden 骤降至 ≈ 21.6 MB**（回收约 919 MB） |
| 12:01–12:14 | Eden 从 56 MB 逐步回升至 ≈ 468 MB（进入下一轮填充） |
| 全窗口 | **Code Cache（CodeHeap）used 恒为 ≈39.1–39.3 MB，max=128 MB，无任何尖峰** |

---

## 3. 证据表

| 证据 | 工具/参数 | 关键返回值 |
|---|---|---|
| 标识无冲突、目标组件确认 | discover_resource_context（region/businessId/envId/instanceId/monitorItemId/IP/alarmId/appName） | apm_app_name=DPSouthProxyService、apm_app_id=149064、apm_env_id=2297428（上游 sourceApi=ApmSearchApplication 确认）；apm_instance_id=2141002（USER_PROVIDED）；无 ambiguous、无候选；missingCapabilities=lts_log_group_id |
| monitorItem 映射 | show_env_monitor_items(envId=2297428) | monitorItemId=121982 = collectorName=JVM、displayName=JVM监控、collectorId=18、collectInterval=60s |
| JVM 视图结构 | show_apm_monitor_item_view_config(collectorId=18, envId=2297428) | 含 memoryPool（groupBy=name：max/used/init/committed）、memory、cpu、thread、memoryUsageRatio 等 |
| 内存池趋势（目标实例） | show_apm_trend(instanceId=2141002, monitorItemId=121982, memoryPool, 11:45–12:15) | [name=Code Cache] used ≈39 MB（max=128 MB）恒定；[name=Par Eden Space] used 11:59 达 940.618 MB、max=960 MB，12:00 骤降至 21.6 MB |
| 告警记录（规则元数据） | list_apm_alarm_data(businessId=111092, ipAddress=10.99.10.132, 11:40–12:20) | id=529623869、regionAlarmEventId=16557997；appName=DPSouthProxyService/china2023_pvms；instanceId=2141002；alarmRuleName=“Warroom风险告警-模型管理-DPModelProxyService微服务CodeCache空间不足-16_4190”；alarmRuleExpression=(((used*1.0)/(max==0?false:max))>0.9)；templateId=8465、alarmRuleId=17680、alarmRuleType=TEMPLATE、collectorId=18；first=11:57:46、last=11:59:46、status=RECOVER |
| 同规则波及面 | 同一 list_apm_alarm_data 结果 | 同名规则在 12:15–12:20 还作用于 NetEcoPVMSDriverService、多台 DPSouthProxyService（10.99.1.72/10.99.3.165/10.99.10.2 等）实例，均为 2 分钟级 RECOVER |

**字节级对照（关键）**：告警 used=986309632 ÷ 1048576 = **940.618 MB**，与目标实例 Par Eden Space used 在 11:59 的 940.6181640625 MB 完全一致；告警 max=1006632960 = 960 MB，与 Par Eden Space max 一致。而 Code Cache max=128 MB，与告警 max=960 MB 完全不匹配。

---

## 4. 根因假设与置信等级

**根因（置信：高，约 85–90%）**：这是**误报**，由告警模板「指标错配 + 命名残留」叠加造成：

1. **指标错配**：模板名/规则名为“CodeCache 空间不足”，但表达式 used/max > 0.9 中的 used/max 实际取到的是 **JVM 堆年轻代 Par Eden Space**（940.6/960 MB），而非 CodeCache 内存池（39/128 MB）。Eden 在两次 minor GC 之间自然填满，越过 90% 阈值即触发告警，GC 后即恢复。
2. **命名残留**：规则名含「DPModelProxyService」，但 alarmRuleType=TEMPLATE 且实际作用在 DPSouthProxyService、NetEcoPVMSDriverService 等多服务上，服务名是模板创建时的残留，非本次告警真实服务。
3. **阈值过敏感（次要）**：对“填满即回收”的年轻代（Eden）套用 90% 使用率阈值，本质是噪声源——Eden 到达 90% 是 GC 前常态，不是故障。

**排除项**：
- ❌ CodeCache 真耗尽：CodeCache used 恒为 39/128 MB（30.7%），无尖峰。
- ❌ 阈值/最大值配置本身错误：960 MB 是 Eden 最大值（正常），CodeCache 128 MB 也未满；问题在于“模板把 Eden 当成 CodeCache”。
- ✅ **实例/服务标签错配**（规则名 DPModelProxyService vs 实际 DPSouthProxyService）+ **指标语义错配**（CodeCache 名 vs Eden 值）是本告警主因。

---

## 5. 已确认事实 vs 未确认项

**已确认事实**：
- 告警 16557997 作用对象是 DPSouthProxyService/china2023_pvms（instanceId 2141002，IP 10.99.10.132），非 DPModelProxyService。
- 规则表达式为 used/max > 0.9，是 TEMPLATE（templateId=8465）。
- 告警 used/max 数值与 Par Eden Space 逐字节吻合，与 Code Cache 池（128 MB）不符。
- 目标实例 CodeCache 全程 ≈39 MB（30.7%），无异常；Eden 12:00 发生 minor GC 后骤降，告警已 RECOVER。

**未确认项（能力/数据缺口）**：
- 该模板的 used/max 字段为何解析到 Eden 而非 CodeCache 的上游配置根因（需在华为云 APM 告警模板编辑界面核对字段绑定）。
- 无法获取 GC 日志佐证（APM→LTS 映射缺失，见下）。

**工具能力缺口**：
- discover_resource_context 明确返回 missingCapability=lts_log_group_id（现有工具无 APM→LTS 确定性映射），故未猜测日志组/流，未查 LTS。
- JVM 视图无显式「CodeCache 使用率」trend 视图，CodeCache 用量需从 memoryPool（sumtable）按 groupBy=name 拆出（本次已做到）。
- list_apm_alarm_data 首次带 region+monitorItemId 组合被上游拒绝（UPSTREAM_INVALID_PARAM），改为 businessId+ipAddress+时间窗后成功（已记录该参数约束）。

---

## 6. 处置建议

**短期处置**：
- 本告警无需应急处理（服务未受影响，CodeCache 健康，Eden 已 GC 回收）。
- 若需立即降噪：将 templateId=8465 的告警暂时静默/降级；单纯上调阈值（如 0.95）仍会误报，本质需修指标绑定。

**长期修复**：
1. 在华为云 APM 告警模板（templateId=8465）中，把 used/max 的指标字段**重新绑定到 CodeCache（CodeHeap）内存池**，而非默认/堆年轻代；并复核该模板被应用到哪些服务。
2. 修正模板命名：将“DPModelProxyService…CodeCache空间不足”改为与实际作用范围一致的通用名（如“JVM CodeCache 使用率”），避免服务名残留误导排障。
3. 对“填满即回收”类指标（Eden/年轻代）不要用固定 90% 阈值；改用 GC 频率/停顿/晋升速率等更有意义的指标，或对 Eden 使用率告警做持续时长（如连续 N 分钟）抑制。

**下一步最小补证**（如需钉死根因）：
1. 打开模板 8465 的指标绑定，确认 used/max 实际指向哪个字段（一次人工核对即可闭环）。
2. 如需代码级佐证 CodeCache 无压力，可取该实例 JVM 参数确认 -XX:ReservedCodeCacheSize（本报告从内存池读得 max=128 MB）。

---

## 7. 诊断调用清单（实质性 MCP tool call，共 7 次）

1. discover_resource_context — 成功（无冲突、无候选、missing=lts_log_group_id）
2. show_env_monitor_items — 成功（121982→collectorId 18）
3. show_apm_monitor_item_view_config — 成功（JVM 视图/memoryPool）
4. show_apm_trend（memoryPool）— 成功（Code Cache 39MB 恒定 + Eden 940.6MB 峰值→GC）
5. list_apm_alarm_data（region+monitorItemId）— 失败（UPSTREAM_INVALID_PARAM，已记录参数约束）
6. list_apm_alarm_data（businessId+时间窗）— 成功（规则元数据 + 波及面）
7. list_apm_alarm_data（ipAddress=10.99.10.132）— 成功（命中 regionAlarmEventId=16557997，status=RECOVER）

（另有一次 tools/list 与 schema 查询用于发现工具契约，不计入实质性调用。）