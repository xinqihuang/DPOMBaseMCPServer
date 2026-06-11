# T24 — CES namespace 受控枚举 + 强制走 list_ces_metrics 发现链（v2）

> 状态: **Done（v2 修订）** · 估时: 1d · 依赖: 现有 `list_ces_metrics`/`batch_query_ces_metric_data`/`query_ces_metric_data`（已存在）、§4.3 · 关联: ADR-004 修订段

## 修订记录

- **v2（2026-06-11）**：回滚 v1 的「SYS_RDS 形态 fallback + `resolved_namespace` 回显」——
  该机制要求 namespace 在 service/adapter 间穿层改写、响应 DTO 加非上游字段、批量路径逐项 zip 回填，
  实现复杂度与可读性代价超过收益。改为：**`SYS.RDS_MYSQL_CLUSTER` 进枚举（第 15 个值）**，
  形态判定交给 Agent 自编排（工具描述指引「不确定时先用带缓存的 `list_ces_metrics` 探测
  哪个 namespace 下存在该实例的指标定义」）。服务端回到「校验 + 透传」，无隐式路由、无响应侧附加字段。
  与 T23 的「发现真值转发、Agent 自编排」哲学一致。**缓存能力保留**（同时也是探测的成本保障）。
- v1（2026-06-11 上午）：含服务端 fallback 版本，已回滚，实现细节不再适用。

## 背景

`CesBatchMetricQuery` 与两个查询工具的 `namespace`/`metricName`/`dimensions` 原为自由 String，
大模型得凭先验拼 `SYS.ECS`/`cpu_util`/维度名。分轴解法：

- **namespace**：全局固定目录 → **§4.3 (a) 受控枚举**。枚举常量经 Spring AI 反射进工具
  JSON Schema，大模型直接看到合法取值。
- **metric_name / 维度名**：按 namespace 变化、海量 → **§4.3 (b) 运行时发现**。
  复用已存在的 `list_ces_metrics`，加缓存。

## 设计（v2 生效版）

**枚举只放请求侧（§4.3），响应侧保持 String 无损（§4.1）：**

- `CesNamespace` 枚举，**15 个受支持取值**，每值携带 `SYS.*` 字面量 + 中文说明；
  `fromValue` 严格拒绝未知值，同时接受字面量（`SYS.ECS`）与常量名（`SYS_ECS`）两种写法。
- 工具入参层 namespace 用枚举：`list_ces_metrics`（可选）、`query_ces_metric_data`、
  `batch_query_ces_metric_data`（工具入参项 `CesBatchMetricQueryInput`）。
  `.getValue()` 映射集中在 Tool 层（`ToolValidations.cesNamespaceValue`）。
- **adapter 请求/响应 DTO 一律保持 String**（§4.1 无损；adapter 不感知枚举）。

**枚举值（15 个）**：

```
SYS_ECS / SYS_OBS / SYS_EVS / SYS_VPC / SYS_GEIP / SYS_DMS / SYS_DCS / SYS_WAF
/ SYS_CFW / SYS_APIG / SYS_RDS / SYS_RDS_MYSQL_CLUSTER / SYS_ELB / SYS_DNS / SYS_NAT
```

> **RDS 双命名空间的处理（v2）**：主备/单机 = `SYS.RDS`，MySQL 集群版 = `SYS.RDS_MYSQL_CLUSTER`，
> 两个值都显式进枚举。Agent 不确定实例形态时，先调 `list_ces_metrics(namespace, dim)` 探测
> 存在性（结果走 1 天缓存，几乎零成本），用有指标定义的那个 namespace 查数——
> 工具描述已写明该流程，禁止瞎猜。服务端不做任何隐式 namespace 替换。

**调用链（Agent 自编排）**：选 `namespace`(枚举) → `list_ces_metrics(namespace)` 拿真实
`metric_name`+维度名（RDS 场景顺带确定 namespace 归属）→ `batch_query_ces_metric_data` /
`query_ces_metric_data` 查数。

**缓存**：`list_ces_metrics` 加 Caffeine TTL 缓存（`ces.discovery-cache`，默认 1d / 2000 条，
key = 整个请求 record，失败/空不缓存，留 `@CacheEvict` 整体失效口）。

## 范围（v2 生效版）

**做**：
1. `CesNamespace` 枚举（15 值）+ 三处工具入参枚举化 + Tool 层 `.getValue()` 映射。
2. 工具描述收紧：发现链顺序、metric_name/维度名禁止编造、RDS 双 namespace 探测指引。
3. `list_ces_metrics` Caffeine 缓存 + evict 口 + `application.yml` 配置。
4. 非枚举 namespace 由框架（枚举反序列化失败）拒绝。

**不做**：
- ❌ **不做服务端 SYS_RDS fallback / `resolved_namespace`**（v1 已回滚，见修订记录）
- ❌ 不枚举 metric_name（走发现）；不静态目录
- ❌ 不改响应 DTO（§4.1 无损，String namespace）
- ❌ 不枚举全部华为云 namespace（只 15 个支持项）

## 验收标准

- [x] `CesNamespace` 含且仅含 15 值，每值带 `SYS.*` 与中文说明；双写法解析、未知值拒绝
- [x] 三工具 namespace 入参为枚举；JSON Schema 中可见全部合法取值
- [x] 传非法 namespace → 框架拒绝或 `INVALID_PARAM`
- [x] 两查询工具描述含「metric_name 取自 list_ces_metrics、禁止编造、调用顺序、RDS 探测指引」
- [x] `list_ces_metrics` 命中 Caffeine 缓存（同参二次 `verify(adapter, times(1))`）；空结果不缓存
- [x] 响应 DTO namespace 仍 String、无附加字段；既有契约测试仍绿
- [x] 全量 `mvn verify` 一次通过；Checkstyle 0；依赖方向不破

## AI 易错点提醒

1. **枚举只动请求侧**，响应 DTO（CesMetricInfo 等）的 namespace 是无损 String，别改。
2. **服务端不做 namespace 隐式替换**——RDS 形态判定是 Agent 的事，别把 fallback 加回来。
3. `.getValue()` 映射集中在 Tool 层一处（`ToolValidations.cesNamespaceValue`），别散落。
4. 缓存键含全部参数（请求 record 值语义）；失败/空结果不写缓存。
5. 与真实 SDK 冲突 → 停下来问（CLAUDE.md §5.1）。

## 完成后

- v1 PR：`refactor(T24): CesNamespace enum + list_ces_metrics cache + SYS_RDS cluster fallback`
- v2 PR：`refactor(T24v2): drop server-side RDS fallback/resolved_namespace; expose SYS.RDS_MYSQL_CLUSTER in enum; keep cache`
