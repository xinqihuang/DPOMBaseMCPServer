# T31 — CES namespace 工具入参由枚举改为 String（修 Spring AI 参数绑定 400）

> 状态: **Done** · 估时: 0.5d · 依赖: T24 · 影响: agentic-mcp（CesMetricsTool / CesMetricDataTool / ToolValidations 及其单测 / CesNamespaceSchemaTest）

## 背景与动机

2026-06-29 用真实凭证（cn-north-9）验证 CES tool 时发现：`list_ces_metrics` / `query_ces_metric_data`
在 Agent 传 `namespace="SYS.ECS"`（正是 `@ToolParam` 描述与 `@JsonValue` 要求的写法）时返回
`isError=true`，文本为 `No enum constant ...CesNamespace.SYS.ECS`。

根因（反编译 `spring-ai-model` 1.0.4 的 `org.springframework.ai.util.json.JsonParser.toTypedObject` 确认）：
当工具方法参数类型 `Class.isEnum()` 为 true 时，Spring AI **直接调 `Enum.valueOf(cls, value.toString())`，
完全绕过 Jackson**，因此 `CesNamespace` 上的 `@JsonCreator fromValue`（兼容 `SYS.ECS` / `SYS_ECS`）形同虚设。
`Enum.valueOf` 要的是枚举常量名 `SYS_ECS`，而 tool 描述让 Agent 传字面量 `SYS.ECS`，必然失败。

影响面（实测）：
- `list_ces_metrics`（`namespace` 是直接 `CesNamespace` 参数）❌
- `query_ces_metric_data`（同上）❌
- `batch_query_ces_metric_data`（`metrics` 是 `List<CesBatchMetricQueryInput>`，走 Jackson `fromJson` 路径，`@JsonCreator` 生效）✅
- `list_alarms`（`namespace` 是 `String`）✅

## 设计

把受影响工具的 `namespace` 参数类型从 `CesNamespace` 改为 `String`，在 Tool 层用
`CesNamespace.fromValue` 做服务端目录翻译（CLAUDE.md §4.3 (a)）。`fromValue` 已兼容
`SYS.ECS`（API 字面量）与 `SYS_ECS`（常量名）两种写法，非法值映射为 `INVALID_PARAM`。

1. **`ToolValidations` 新增 `resolveCesNamespace(String)`**：
   - `null` → 返回 `null`（保持「必填校验由 service 层完成」的既有语义，list 的 namespace 可选、query 缺失时由 service 拒绝）
   - 非 `null` → `CesNamespace.fromValue(value).getValue()`，`IllegalArgumentException` → `InvalidParamException`
   - 保留既有 `cesNamespaceValue(CesNamespace)` 供 `CesBatchMetricDataTool` 继续使用（batch 仍用枚举形态的 record 字段，不受本 bug 影响）
2. **`CesMetricsTool.listCesMetrics`**：`CesNamespace namespace` → `String namespace`，`cesNamespaceValue(namespace)` → `resolveCesNamespace(namespace)`，`@ToolParam` 描述由「closed enum」改为「closed set of 15 values」并保留逐项列举。
3. **`CesMetricDataTool.queryCesMetricData`**：同上。

## 不做

- ❌ 不改 `batch_query_ces_metric_data` / `CesBatchMetricQueryInput`（枚举在 record 字段内，走 Jackson 路径，正常；保留其 Schema 封闭集）
- ❌ 不改 `CesNamespace` 枚举本身（`@JsonCreator fromValue` 设计正确，问题在 Spring AI 绑定层）
- ❌ 不改 adapter / monitoring / 响应 DTO（namespace 在 adapter 边界本就是 String）
- ❌ 不引入自定义 Spring AI 参数转换器（升级风险大，且 String+fromValue 已满足 §4.3 (a)）

## 验收

- [x] `CesMetricsToolTest` / `CesMetricDataToolTest` 改为传 `"SYS.ECS"` 字符串，全绿
- [x] `CesNamespaceSchemaTest` 去掉 list/query 的 Schema 枚举断言（参数已非枚举），保留 batch 断言
- [x] `mvn -pl agentic-mcp -am test` + `mvn checkstyle:check` 通过
- [x] 真实回归：用 cn-north-9 凭证启动服务，`list_ces_metrics` / `query_ces_metric_data` 传 `namespace="SYS.ECS"` 不再 400，返回真实数据

## AI 易错点提醒

- `resolveCesNamespace` 必须对 `null` 透传，否则破坏 list 的「namespace 可选」语义
- 改测试时 `NS` 常量由 `CesNamespace` 改为 `String`，`req.namespace()` 断言改为字面量 `"SYS.ECS"`
- `@ToolParam` 描述里仍要逐项列出 15 个合法值——Schema 不再自动生成 enum，描述文本是 Agent 唯一的封闭集提示
