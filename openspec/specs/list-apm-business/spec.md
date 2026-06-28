# list-apm-business Specification

## Purpose
APM CMDB 发现链第 0 步：列出租户在 APM 注册的全部应用，输出后续所有 APM 工具所需的 `business_id`。
## Requirements
### Requirement: APM 应用列表发现

系统 SHALL 提供只读工具 `list_apm_business`，调用 APM v1 `ListBusiness` 列出当前租户在 APM 注册的全部应用（business）。该工具是 APM CMDB 发现链第 0 步：其返回的 `id` 即后续所有 APM 工具所需的 `business_id`，调用顺序为 `list_apm_business → search_apm_application → show_env_monitor_items → show_apm_monitor_item_view_config → show_apm_trend`。工具无入参，Agent MUST 从本工具获取 `business_id`，不得臆造。

#### Scenario: 列出应用并取得 business_id
- **WHEN** 调用 `list_apm_business`（无入参）
- **THEN** 系统 SHALL 返回 `business_nodes[]`，每项含 `id`（即 business_id）

### Requirement: 应用列表缓存

应用列表为稳定目录，系统 SHALL 接入 APM discovery 缓存（`apm.discovery-cache`，TTL 默认 1d）；空列表或 null MUST 不写缓存。

#### Scenario: 同参二次命中缓存
- **WHEN** 短时间内重复调用
- **THEN** 第二次 SHALL 命中缓存，不再调用上游

### Requirement: BusinessNodeModel 无损投影

响应 DTO `ApmBusinessNode` SHALL 无损覆盖 SDK `BusinessNodeModel` 全部 9 个字段。`default`（JSON 键，Java 关键字）DTO 组件名取 `defaultFlag` 并标注 `@JsonProperty("default")`，与 `is_default` 是两个独立字段，均保留；`gmt_create` / `gmt_modify` 类型贴齐 SDK 的 `LocalDate`（非 String 臆测）。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实 SDK 样本 `sdk-samples/apm/list-business-response.json`（全字段 + 最小字段各一条）
- **WHEN** 反序列化（含 JavaTimeModule）并经 adapter 映射
- **THEN** `ApmBusinessNode` 9 字段 SHALL 逐一断言通过，漂移即 fail

