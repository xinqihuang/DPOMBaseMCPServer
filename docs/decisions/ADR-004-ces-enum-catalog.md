# ADR-004: CES 参数枚举目录（严格枚举 vs 宽容目录）

- 状态: Accepted
- 日期: 2026-06-02
- 关联: `docs/specs/tools/batch_query_ces_metric_data.md`、`docs/tasks/T14-batch-query-ces-metric-data.md`

## Context

CES 的 `ShowMetricData` 与 `BatchListMetricData` 接口的入参（`namespace` / `metric_name` / `dimensions.name` / `filter` / `period`）原本在 DTO 中以 `String` / `Integer` 承载，校验集合放在 Service 层（`Set<String> ALLOWED_FILTERS` 等）。

这带来三个问题：
1. **类型不安全**：Tool → Service → Adapter 全链路用 `String` 传 `filter`，IDE 无法做枚举补全
2. **校验集合散落**：同一组取值在 `CesMetricDataService` 和 `CesBatchMetricDataService` 各写一份
3. **取值文档化弱**：阅读代码看不出 `filter` 合法取值，只能跑去看 SDK 源码或 CES API 文档

但贸然全部改成 Java `enum` 会带来另一个风险——**华为云持续新增服务**，新的 namespace / metric_name 如果被 enum 锁死，会让我们的 MCP 服务无法转发任何超出本地枚举范围的合法查询。

## Decision

按"上游变更频率"分两档处理：

### 严格枚举（DTO 直接持有 enum 类型）

`filter` 和 `period` 的取值在 CES API 文档中是**封闭集**且多年未变：
- `CesMetricFilter`：`average` / `max` / `min` / `sum` / `variance`
- `CesMetricPeriod`：1 / 60 / 300 / 1200 / 3600 / 14400 / 86400 秒

这两个枚举：
- DTO 字段类型直接是 enum
- `fromValue(...)` / `fromSeconds(...)` 对未知值**抛 `IllegalArgumentException`**
- Tool 层 catch 之并转换为 `InvalidParamException`（错误码 `INVALID_PARAM`）
- Service 层不再需要 `ALLOWED_*` Set 校验（被类型系统强制）

### 宽容目录（仅作为常量字典，DTO 仍持有 String）

`namespace` / `metric_name` / `dimensions.name` 的取值会随华为云上新服务持续扩展：
- `CesNamespace`：枚举常用 namespace（SYS.ECS、SYS.RDS、SYS.EVS 等）
- `CesDimensionKey`：枚举常用维度键（instance_id、disk_name、bucket_name 等）
- `CesMetric`：枚举 ECS 基础监控指标目录（19 条，每条携带 namespace + 主维度 + 单位 + 中文描述）

这三个枚举：
- **DTO 字段仍是 `String`**，允许 Agent 透传未列入枚举的取值到 SDK
- `fromValue(...)` 对未知值**返回 `null`**（不抛异常）
- 提供 `getValue()` / `getId()` 作为常量供代码引用，避免硬编码字符串
- 提供 `find(namespace, id)` 等查找方法供 UI / 文档生成

### 校验分层

- **Tool 层**：基本校验
  - 必填非空（filter、period）
  - 枚举字面量解析（filter、period）
- **Service 层**：业务规则
  - `from < to`
  - `dimensions` 长度 [1, 4]，name/value 非空
  - `metrics` 长度 [1, 500]（批量 tool）
  - `namespace` 正则格式 `^[A-Z][A-Za-z0-9]{2,31}\.[A-Za-z0-9_]+$`

## Consequences

- ✅ `filter` / `period` 从写入 DTO 到调 SDK 全程类型安全
- ✅ Service 层删掉重复的 `ALLOWED_*` 集合，约 20 行代码消失
- ✅ `CesMetric` 枚举可作为 LLM prompt context 或 README 自动生成的来源
- ✅ 新增华为云服务 / metric 不需要改代码即可被 MCP 转发
- ⚠️ 同一字段在不同层之间存在 `String ↔ enum` 转换，需要在 Tool 层和 Adapter 层各做一次（已通过测试覆盖）
- ⚠️ `CesNamespace` / `CesMetric` 目录会随时间过期；定期巡检需要核对 [CES API 文档](https://support.huaweicloud.com/intl/en-us/api-ces/)

## Alternatives Considered

### A. 全部用 String + Set 校验（现状回退）

- ✅ 改动量小
- ❌ 类型不安全，无 IDE 补全
- ❌ 校验集合在多处重复
- ❌ 文档化弱
- 否决理由：违背"AI 易错点"治理目标——String 让 AI 写实现时容易拼错字面量

### B. 全部用严格 Java enum（含 namespace / metric）

- ✅ 全链路类型安全
- ❌ 新华为云服务上线后必须先发 MCP Server 版本才能用，运营成本不可接受
- 否决理由：违背华为云 SDK "前向兼容、后向可扩展" 的契约

### C. 用 SDK 风格的"开放枚举类"（非 Java enum，含 `fromValue` 工厂）

参考 SDK 内部 `Filter` / `PeriodEnum` 的实现：`final class` + 静态常量 + 对未知值返回新实例。

- ✅ 兼顾类型安全和扩展性
- ❌ 写法非主流 Java，团队评审成本高
- ❌ Jackson 序列化要额外配置
- 否决理由：当下需求用"分档处理"已能覆盖，开放枚举类增加复杂度无回报

## References

- 实现：`agentic-adapter-ces/src/main/java/.../dto/CesMetric*.java`、`CesNamespace.java`、`CesDimensionKey.java`
- 数据来源：https://support.huaweicloud.com/intl/en-us/usermanual-ecs/ecs_03_1002.html（ECS 基础监控指标）
- 关联 spec：`docs/specs/tools/batch_query_ces_metric_data.md` §4
- 提交：`ce6fd6c feat(ces): add batch_query_ces_metric_data tool and CES enum catalog`

## 2026-06-11 更新（T24）：namespace 从"宽容目录"升级为请求侧受控枚举

T24（`docs/tasks/T24-ces-namespace-enum.md`）将 `CesNamespace` 的定位从"宽容目录"
（DTO 持 String、`fromValue` 未知值返回 `null`）调整为 **请求侧受控枚举**（CLAUDE.md §4.3 (a)）：

- **请求侧**：三个 CES 查询工具的 namespace 入参直接以 `CesNamespace` 承载（14 个受支持取值），
  枚举常量经 Spring AI 反射进工具 JSON Schema；`fromValue` 对未知值**抛
  `IllegalArgumentException`**（严格拒绝），并同时接受 API 字面量（`SYS.ECS`）与常量名（`SYS_ECS`）
  两种写法以兼容 Schema 渲染差异。
- **响应侧与 adapter 边界不变**：响应 DTO 与 adapter 请求 DTO 的 namespace 仍为 String
  （§4.1 无损投影；adapter 边界还需承载不进枚举的 `SYS.RDS_MYSQL_CLUSTER`，
  由 service 层 SYS_RDS 形态 fallback 透明路由）。
- 原"宽容目录"理由（华为云持续新增服务）对**诊断 Agent 的请求侧**不再成立：
  Agent 凭先验拼 namespace 猜错时上游返回空数据而非报错，会无声带偏诊断（T23/T24 同源结论），
  封闭集 + 显式扩容（改枚举发版）是更安全的运营模式。`metric_name` / 维度名仍走
  `list_ces_metrics` 运行时发现（§4.3 (b)），不受本次调整影响。
