# Spec: show_apm_trend

> 状态: Approved · 版本: v1.0 · 所属服务: DPOMBaseMCPServer

## 1. 意图与场景

让智能运维 Agent 拉取 APM 监控项的趋势图原始数据（折线/汇总/明细表），用于：

- 在告警发生时回看该指标过去 N 分钟/小时的曲线，判断是抖动还是趋势恶化
- 串联 `list_apm_alarm_data` → `show_apm_trend`：先看告警，再可视化告警绑定的指标
- 自由查询某个 `monitor_item` 在指定时间窗的曲线（不依赖告警入口）

定位: APM 监控数据查询工具，**与告警工具并列**。趋势数据来自 APM 自身指标存储，
跟 CES 的 metric_data 是两条独立链路，不要混淆。

## 2. 范围边界

**做**:
- 调用 APM `ShowTrend`，拉取一组 `monitor_item` × `view_config` × 时间窗的曲线/表格
- 响应**无损覆盖** `ShowTrendResponse` 全部字段（`line_list` × `FrontLine` 6 字段，
  含 `point_list` × `FrontPoint` 2 字段；`latest_data_Time`）
- 请求体 `TrendParam` 全部 6 字段全暴露（其中 `view_config` 作为嵌套 record 暴露）
- 复用 `huaweicloud.apm-business-id` 默认值
- 上游异常映射

**不做**:
- 不做客户端聚合 / 不做时间窗自动对齐 / 不做曲线渲染
- 不解析 `FrontPoint.value`（SDK 类型是 `Object`，DTO 保留 `Object`，由 Agent 自行判断 Number/String）
- 不修正 SDK 字段名 `latest_data_Time` 的大小写（保留原样，避免破坏 SDK 反序列化）

## 3. MCP Tool 接口契约

### 3.1 Tool 注册元数据

- name: `show_apm_trend`
- description（Agent 看到的）:

  > Fetch APM monitor-item trend data (line points or aggregation table) for a
  > given time window. Use this AFTER you identify a monitor_item_id (e.g. from
  > an APM alarm's monitor_item_id, or from a known APM application's monitor
  > config). The result contains one or more lines, each with a list of
  > {time, value} points; value is loosely typed (number or string per SDK).
  > Pair with list_apm_alarm_data when you need to inspect the metric behind an
  > alarm. Start/end time are ISO-8601 strings forwarded to upstream.

- annotations: `readOnlyHint=true` / `destructiveHint=false` / `idempotentHint=true`

### 3.2 输入参数（17 字段，扁平 5 + 嵌套 viewConfig 12）

**Outer 平铺**（5 个 `@ToolParam`）:

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `business_id` | long | 否 | APM 业务 id（HTTP `x-business-id`），null 时回落到 `huaweicloud.apm-business-id` 配置 |
| `instance_id` | long | 否 | APM 实例 id |
| `monitor_item_id` | long | 否 | 监控项 id（核心过滤维度） |
| `env_id` | long | 否 | 环境 id |
| `start_time` | string | 是 | 开始时间，原样透传给上游 |
| `end_time` | string | 是 | 结束时间，原样透传给上游 |
| `view_config` | object | 是 | 视图配置，见 3.3 |

**业务校验**（service 层）:
- `start_time` / `end_time` 必填非空 → 否则 `INVALID_PARAM`
- `view_config` 必填非 null
- `view_config.view_type` 必须 ∈ {`trend`, `sumtable`, `rawtable`}（SDK 枚举）
- `view_config.metric_set` 必填非空
- `business_id` 与默认都为 null → `INVALID_PARAM`

### 3.3 viewConfig 嵌套结构（12 字段，对齐 SDK `TrendView`）

DTO `ApmTrendViewConfig`（record）字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `view_type` | string | 是 | `trend` / `sumtable` / `rawtable` |
| `collector_name` | string | 否 | 采集器名 |
| `metric_set` | string | 是 | 指标集名 |
| `title` | string | 否 | 图表标题 |
| `table_direction` | string | 否 | `H`（默认）/ `V` |
| `group_by` | string | 否 | 分组字段 |
| `filter` | string | 否 | 过滤表达式 |
| `field_item_list` | list | 否 | 字段配置数组，元素见 3.4 |
| `span` | bool | 否 | 是否跨度 |
| `span_field` | string | 否 | span 字段属性 |
| `order_by` | string | 否 | 排序 |
| `latest` | string | 否 | latest 表达式 |

### 3.4 fieldItemList 元素（7 字段，对齐 SDK `FieldItem`）

DTO `ApmTrendFieldItem`（record）字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `function` | string | 表达式 |
| `as` | string | as 别名 |
| `default_value` | string | 默认值 |
| `trace` | bool | 是否 trace |
| `precision` | int | 精度（百分比） |
| `unit` | string | 单位 |
| `visible` | bool | 是否可见 |

### 3.5 输出契约（成功）

```json
{
  "line_list": [
    {
      "title": "avg(latency)",
      "unit": "ms",
      "precision": 2,
      "data_type": "double",
      "visible": true,
      "point_list": [
        { "time": 1717999800000, "value": 123.4 },
        { "time": 1717999860000, "value": 130.1 }
      ]
    }
  ],
  "latest_data_time": 1717999860000
}
```

字段说明：
- `line_list[*]` 是 `FrontLine` 全 6 字段的无损投影
- `point_list[*]` 是 `FrontPoint` 全 2 字段；`value` DTO 类型 `Object`，输出时 Jackson 按实际类型序列化（数字/字符串）
- `latest_data_time` 对应 SDK `latest_data_Time`（**DTO 内 Java 字段名 `latestDataTime`，JSON 序列化输出 `latest_data_time`**——内部统一 snake_case 输出，不暴露 SDK 大小写瑕疵到外部契约）

### 3.6 输出契约（失败）

标准 `ErrorResponse`，错误码沿用全局表。

## 4. 与华为云 SDK 的映射

- **SDK 类**：`com.huaweicloud.sdk.apm.v1.ApmClient`
- **SDK 方法**：`showTrend(ShowTrendRequest)`
- **SDK 版本**：v3.1.177
- **HTTP**：`POST /v1/apm2/openapi/view/trend/show`，body = `TrendParam`，header = `x-business-id: Long`

### 4.1 请求映射

| DTO `ApmTrendRequest` | SDK |
|---|---|
| businessId | `ShowTrendRequest.xBusinessId`（header） |
| viewConfig | `TrendParam.viewConfig`（嵌套对象映射，见 4.2） |
| instanceId | `TrendParam.instanceId` |
| monitorItemId | `TrendParam.monitorItemId` |
| envId | `TrendParam.envId` |
| startTime | `TrendParam.startTime` |
| endTime | `TrendParam.endTime` |

### 4.2 viewConfig 子映射

DTO `ApmTrendViewConfig` ↔ SDK `TrendView`：12 字段一一对应。其中 `viewType` /
`tableDirection` 是 SDK 枚举，adapter 用 `TrendView.ViewTypeEnum.fromValue(...)` /
`TrendView.TableDirectionEnum.fromValue(...)` 反向映射（非法值 → `INVALID_PARAM`）。

`fieldItemList` 元素 `ApmTrendFieldItem` ↔ SDK `FieldItem`：7 字段一一对应。

### 4.3 响应映射（全字段无损）

| SDK `ShowTrendResponse` | DTO `ApmTrendResponse` |
|---|---|
| line_list (`List<FrontLine>`) | lineList (`List<ApmTrendLine>`) |
| latest_data_Time (`Long`) | latestDataTime (`Long`, JSON 出参为 `latest_data_time`) |

| SDK `FrontLine` | DTO `ApmTrendLine` |
|---|---|
| point_list (`List<FrontPoint>`) | pointList (`List<ApmTrendPoint>`) |
| title (String) | title |
| unit (String) | unit |
| precision (Integer) | precision |
| data_type (String) | dataType |
| visible (Boolean) | visible |

| SDK `FrontPoint` | DTO `ApmTrendPoint` |
|---|---|
| time (Long) | time |
| value (`Object`) | value (`Object`) |

## 5. 非功能要求

- **限流**：复用 `apm-readonly` RateLimiter
- **重试**：复用 `huaweicloud-retryable`（仅对 throttled/5xx/timeout 重试 3 次指数退避）
- **超时 / 可观测**：同 `list_apm_alarm_data`，日志含 businessId / monitorItemId / viewType / 时间窗 / 耗时 / `upstreamTraceId`

## 6. 测试策略（DoD）

| ID | 类 | 用例 |
|---|---|---|
| UT-T1 | Tool | success passthrough — 7 入参（含嵌套 viewConfig）装配 + 返回值透传 |
| UT-T2 | Tool | service `InvalidParamException` → `INVALID_PARAM` |
| UT-T3 | Tool | service `UpstreamException` → ErrorResponse 含 trace id |
| UT-S1 | Service | `viewConfig == null` → INVALID_PARAM |
| UT-S2 | Service | `viewConfig.viewType` 非法值 → INVALID_PARAM |
| UT-S3 | Service | `viewConfig.metricSet` 为空 → INVALID_PARAM |
| UT-S4 | Service | `startTime` / `endTime` 缺失 → INVALID_PARAM |
| UT-S5 | Service | businessId 与默认都为空 → INVALID_PARAM |
| UT-S6 | Service | 全合法 → 委托 adapter |
| UT-A1 | Adapter | viewConfig 字段全映射 + header 注入 |
| UT-A2 | Adapter | 429 / 401 / 5xx / Timeout（4 case） |
| TC-01 | Contract | 样本 JSON 反序列化 + adapter 映射 + lineList/pointList/latestDataTime **全字段**断言；含 `value` 为 Number 和 String 两种情况 |

## 7. 验收标准

- [ ] UT/TC 全部通过
- [ ] MCP Inspector 能看到 `show_apm_trend`，schema 含嵌套 `view_config`
- [ ] 日志含 businessId / monitorItemId / viewType / 时间窗 / 耗时 / trace id
- [ ] Checkstyle 0
- [ ] DTO 无损（响应 8 字段 + viewConfig 12 字段 + fieldItem 7 字段全留）
- [ ] `FrontPoint.value` 保留为 `Object`，契约测试覆盖 Number / String 两种 value
