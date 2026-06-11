# T24 — CES namespace 受控枚举 + 强制走 list_ces_metrics 发现链

> 状态: **Done** · 估时: 1d（含 RDS fallback） · 依赖: 现有 `list_ces_metrics`/`batch_query_ces_metric_data`/`query_ces_metric_data`（已存在）、§4.3 · 关联: 无需改 §4.3（本卡是其 (a)+(b) 的实例）

## 背景

`CesBatchMetricQuery` 与两个查询工具的 `namespace`/`metricName`/`dimensions` 均为自由 String，大模型得凭先验拼 `SYS.ECS`/`cpu_util`/维度名。与 APM 同病，但**分轴解法不同**：

- **namespace**：全局固定目录（SYS.ECS/SYS.VPC/…）→ **§4.3 (a) 受控枚举**。枚举常量会被 Spring AI 反射进工具 JSON Schema，大模型直接看到合法取值。
- **metric_name / 维度名**：按 namespace 变化、海量、分区域 → **§4.3 (b) 运行时发现**。CES `list_ces_metrics` **已存在**，直接复用，不静态目录。

> 现状已具备发现工具，本卡是**收口改造**，非新建：主要 = namespace 枚举化 + 描述指引顺序 + 给发现工具加缓存。

## 设计

**枚举只放请求侧（§4.3），响应侧保持 String 无损（§4.1）：**

- 新增 `CesNamespace` 枚举（14 个受支持 namespace，每个携带 `SYS.*` 字面值 + 中文说明）。
- **工具入参**层把 namespace 改 `CesNamespace`：`list_ces_metrics`、`batch_query_ces_metric_data`（`CesBatchMetricQuery.namespace`）、`query_ces_metric_data`。tool/adapter 用 `.getValue()` 映射回 SDK 需要的 String。
- **响应 DTO 不动**：`CesMetricInfo.namespace`、`CesListMetricsResponse` 等保持 String（discovery 输出，§4.1 无损）。

**调用链（Agent 自编排）**：选 `namespace`(枚举) → `list_ces_metrics(namespace)` 拿真实 `metric_name`+维度名 → `batch_query_ces_metric_data` / `query_ces_metric_data` 查数。
> 维度**名**(instance_id)来自 list_ces_metrics；维度**值**(具体实例)来自告警 payload 的 resource_id/dimensions，两者别混。

## 枚举值（负责人给定，照填）

```
SYS_ECS  ("SYS.ECS",  "弹性云服务器")
SYS_OBS  ("SYS.OBS",  "对象存储服务")
SYS_EVS  ("SYS.EVS",  "云硬盘")
SYS_VPC  ("SYS.VPC",  "虚拟私有云/区域级EIP")
SYS_GEIP ("SYS.GEIP", "全域弹性公网IP")
SYS_DMS  ("SYS.DMS",  "分布式消息服务")
SYS_DCS  ("SYS.DCS",  "分布式缓存服务")
SYS_WAF  ("SYS.WAF",  "Web应用防火墙")
SYS_CFW  ("SYS.CFW",  "云防火墙")
SYS_APIG ("SYS.APIG", "API网关(共享版)")
SYS_RDS  ("SYS.RDS",  "关系型数据库")
SYS_ELB  ("SYS.ELB",  "弹性负载均衡")
SYS_DNS  ("SYS.DNS",  "云解析服务")
SYS_NAT  ("SYS.NAT",  "NAT网关")
```

> **已知边界**：RDS for MySQL **集群版** 的 namespace 是 `SYS.RDS_MYSQL_CLUSTER`，**不进枚举**（避免同资源双 namespace 迫使 Agent 猜部署形态；猜错返回空数据而非报错，会无声带偏诊断）。改由 service 层 **SYS_RDS 形态 fallback** 透明处理，见下节。

## SYS_RDS 形态 fallback（service 层，Agent 无感知）

**问题**：RDS MySQL 按部署形态分裂为两个 namespace（主备/单机=`SYS.RDS`，集群版=`SYS.RDS_MYSQL_CLUSTER`），而 Agent 无法从告警判定形态。**解法**：判定从 Agent 挪到 service——Agent 只见 `SYS_RDS`，service 探测形态后路由。

**探测信号用 `list_ces_metrics` 的存在性，不用查询结果是否为空**（查询为空有歧义：可能是 namespace 错，也可能是该时间窗真没数据；而「该实例在某 namespace 下是否注册了任何指标」是与时间窗无关的干净信号）。探测走本卡新加的 1 天缓存，几乎零成本。

```
Agent 传 SYS_RDS + 实例 dimension
  └─ service: listMetrics(SYS.RDS, dim)                       ← 走缓存
       ├─ 有指标 → 按 SYS.RDS 查数
       └─ 无指标 → listMetrics(SYS.RDS_MYSQL_CLUSTER, dim)     ← 走缓存
            ├─ 有指标 → 按 SYS.RDS_MYSQL_CLUSTER 查数
            └─ 也无 → 明确报错/空 + 「该实例在两个 RDS namespace 下均无指标」
```

**三条纪律**：
1. fallback **只对 `SYS_RDS` 生效**，service 内显式特例 + 注释讲明缘由；**不要**做成「任何 namespace 查不到就乱试」的通用机制。
2. **响应必须带 `resolved_namespace`**（实际取数的 namespace）——不许静默替换，Agent 与诊断报告需知道数据来源。
3. `SYS_RDS_MYSQL_CLUSTER` **不进枚举、不进 Schema**，仅作 service 内部常量。Agent 的世界里 RDS 只有一个值。

适用：`query_ces_metric_data` 与 `batch_query_ces_metric_data` 中 namespace=SYS_RDS 的查询项。`list_ces_metrics` 工具本身不做 fallback（它就是探测原语，Agent 显式传哪个查哪个）。

## 范围

**做**：
1. 新增 `CesNamespace` 枚举（ces 模块 dto 包；14 值 + value + 中文 desc）。
2. 三处工具入参 namespace 改枚举：
   - `CesMetricsTool#list_ces_metrics`
   - `CesBatchMetricDataTool#batch_query_ces_metric_data`（`CesBatchMetricQuery.namespace` → 枚举）
   - `CesMetricDataTool#query_ces_metric_data`
   adapter/service 内 `.getValue()` 映射回 SDK String；SDK 调用与响应不变。
3. 工具描述收紧：
   - `list_ces_metrics` 描述：「先选 namespace（枚举），用本工具发现该 namespace 下真实 metric_name 与维度」。
   - 两查询工具描述：「`metric_name`/维度名**取自 list_ces_metrics 返回，禁止自行编造**；调用顺序 list_ces_metrics → query」。
4. `list_ces_metrics` 加 **Caffeine TTL 缓存**（同 T23：`expireAfterWrite=1d`，`maximumSize` 设上限，失败/空不缓存，留 evict 口）。key = 全部请求参数（namespace + dimName + dimValue + limit + start + order）。
5. 非枚举 namespace 入参由框架（枚举反序列化失败）或 service 校验 → 走 `INVALID_PARAM`。
6. **SYS_RDS 形态 fallback**（见上节）：两查询 service 路径加探测路由；响应 DTO 加 `resolved_namespace`（仅 RDS fallback 时与入参不同，其余等于入参值）；`SYS.RDS_MYSQL_CLUSTER` 为 service 内部常量。
7. fallback 测试两例：主备实例命中 SYS.RDS 不触发 fallback；SYS.RDS 无指标时探测 cluster 并以 `resolved_namespace=SYS.RDS_MYSQL_CLUSTER` 返回。

**不做**：
- ❌ 不新建 list_ces_metrics（已存在，只改入参+描述+缓存）
- ❌ 不枚举 metric_name（走发现）；不静态目录
- ❌ 不改响应 DTO 的 String namespace（§4.1 无损）
- ❌ 不枚举全部华为云 namespace（只 14 个支持项）
- ❌ `SYS.RDS_MYSQL_CLUSTER` 不进枚举/Schema（由 service fallback 处理，见上节）
- ❌ fallback 不泛化到其它 namespace（仅 SYS_RDS 特例）

## 关键技术要求

- **枚举仅在请求侧**；响应/SDK 边界保持 String。`.getValue()` 双向映射放 tool 或 adapter 一处，别散。
- Spring AI 需能把枚举反射进 @ToolParam schema（确认 `@ToolParam` 对 enum 的渲染；必要时枚举加描述）。
- 缓存键含全部查询参数，避免不同维度过滤命中同一缓存。
- 既有 `batch_query`/`query` 的响应、分页、错误码不动。

## 验收标准

- [x] `CesNamespace` 枚举含且仅含 14 值，每值带 `SYS.*` 与中文说明
- [x] 三工具 namespace 入参为枚举；JSON Schema 中可见 14 个合法取值
- [x] 传非法 namespace → `INVALID_PARAM`
- [x] 两查询工具描述含「metric_name 取自 list_ces_metrics、禁止编造、调用顺序」
- [x] `list_ces_metrics` 命中 Caffeine 缓存（`expireAfterWrite=1d`）；连续两次同参第二次不打 adapter（`verify(adapter, times(1))`）
- [x] 响应 DTO namespace 仍 String，无损；既有契约测试仍绿
- [x] SYS_RDS fallback：主备实例不触发；SYS.RDS 无指标→自动按 cluster 查并返回 `resolved_namespace=SYS.RDS_MYSQL_CLUSTER`；两 namespace 均无→明确报错
- [x] 响应含 `resolved_namespace` 字段（非 RDS 场景等于入参值）
- [x] 迭代 `mvn -o -q -pl agentic-mcp -am test`；收尾全量一次通过；Checkstyle 0；依赖方向不破

## AI 易错点提醒

1. **枚举只动请求侧**，别把响应 DTO（CesMetricInfo 等）的 namespace 也改枚举——那是无损 String。
2. **metric_name 不枚举、不静态目录**——走 list_ces_metrics 发现。
3. **list_ces_metrics 已存在**，别重建；只加入参枚举 + 描述 + 缓存。
4. `.getValue()` 映射集中一处，别在多层重复散落。
5. **fallback 判定信号是 listMetrics 存在性，不是查询数据是否为空**——空数据有歧义（可能只是时间窗无数据），别用它触发 fallback。
6. **不许静默替换 namespace**：fallback 命中 cluster 必须在响应 `resolved_namespace` 体现。
7. `SYS_RDS_MYSQL_CLUSTER` 别加进枚举/Schema——它只是 service 内部常量。
8. 缓存键含全部参数；失败/空结果不写缓存。
9. 迭代只编单模块单测试（`-o -q -pl ... -am`），收尾再全量。
10. 与真实 SDK 冲突 → 停下来问（CLAUDE.md §5.1）。

## 完成后

PR：`refactor(T24): CesNamespace enum + list_ces_metrics cache + SYS_RDS cluster fallback`
