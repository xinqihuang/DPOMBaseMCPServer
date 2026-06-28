> 存量回填（v2 修订版生效）：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T24-ces-namespace-enum.md`，状态 Done；v1 服务端 fallback 版本已回滚，不在交付范围）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. CesNamespace 枚举（agentic-mcp）

- [x] 1.1 新增 `CesNamespace` 枚举，含且仅含 15 值（`SYS_ECS` … `SYS_NAT`，含 `SYS_RDS` 与 `SYS_RDS_MYSQL_CLUSTER`），每值携带 `SYS.*` 字面量 + 中文说明
- [x] 1.2 `fromValue` 严格拒绝未知值；同时接受字面量写法（`SYS.ECS`）与常量名写法（`SYS_ECS`）
- [x] 1.3 `.getValue()` 返回 SDK 侧字面量

## 2. 工具入参枚举化（agentic-mcp）

- [x] 2.1 `list_ces_metrics` namespace 入参改用 `CesNamespace`（可选）
- [x] 2.2 `query_ces_metric_data` namespace 入参改用 `CesNamespace`（必填）
- [x] 2.3 `batch_query_ces_metric_data` 工具入参项 `CesBatchMetricQueryInput` 的 namespace 改用 `CesNamespace`
- [x] 2.4 枚举 → String 的 `.getValue()` 映射集中在 Tool 层一处（`ToolValidations.cesNamespaceValue`）
- [x] 2.5 非枚举 namespace 由框架（枚举反序列化失败）拒绝 → `INVALID_PARAM`

## 3. 工具描述收紧（agentic-mcp）

- [x] 3.1 两查询工具描述写明发现链顺序（选 namespace → `list_ces_metrics` 拿真实 metric_name+维度名 → 查数）
- [x] 3.2 描述写明 `metric_name` / 维度名禁止编造，取自 `list_ces_metrics`
- [x] 3.3 描述写明 RDS 双 namespace（`SYS.RDS` vs `SYS.RDS_MYSQL_CLUSTER`）探测指引，服务端不做隐式替换

## 4. 发现缓存（list_ces_metrics）

- [x] 4.1 `list_ces_metrics` 加 Caffeine 缓存 `ces.discovery-cache`（默认 1d / 2000 条，key=整个请求 record）
- [x] 4.2 失败 / 空结果不写缓存
- [x] 4.3 留 `@CacheEvict` 整体失效口
- [x] 4.4 `application.yml` 配置 `ces.discovery-cache`

## 5. 测试

- [x] 5.1 `CesNamespace` 单测：含且仅含 15 值、双写法解析、未知值拒绝
- [x] 5.2 三工具 namespace 入参为枚举，JSON Schema 中可见全部合法取值
- [x] 5.3 传非法 namespace → 框架拒绝或 `INVALID_PARAM`
- [x] 5.4 `list_ces_metrics` 命中 Caffeine 缓存（同参二次 `verify(adapter, times(1))`）；空结果不缓存
- [x] 5.5 响应 DTO namespace 仍 String、无附加字段；既有契约测试仍绿
- [x] 5.6 全量 `mvn verify` 一次通过；Checkstyle 0；依赖方向不破

## 6. 不做（本期未交付 / 已回滚，遗留项）

- [ ] 6.1 服务端 SYS_RDS 形态 fallback / `resolved_namespace` 回显（v1 已回滚，本期不做，不得加回）
- [ ] 6.2 枚举 `metric_name` / 维度名（走发现，不做静态目录）
- [ ] 6.3 枚举全部华为云 namespace（只保留 15 个支持项）
- [ ] 6.4 改响应 DTO namespace 类型（保持无损 String，不做）
