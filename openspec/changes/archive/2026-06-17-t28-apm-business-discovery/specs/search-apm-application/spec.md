## ADDED Requirements

### Requirement: APM 组件与环境搜索

系统 SHALL 提供只读工具 `search_apm_application`，调用 APM v1 `SearchApplication` 列出指定应用（business）下的组件（app）与环境（env），含每个环境的探针在线 / 手动停止 / 离线计数。该工具是 APM CMDB 发现链第 1 步：返回的 `env_id` 喂给 `show_env_monitor_items`。`business_id` MUST 取自前置 `list_apm_business`（为 null 时回落 `huaweicloud.apm-business-id` 配置），不得臆造。探针计数为运行态信号，该工具 MUST 不缓存。

#### Scenario: 搜索组件环境并取得 env_id
- **WHEN** 提供有效 `business_id`（或配置默认值）与可选 `region`/`keyword`/分页
- **THEN** 系统 SHALL 返回 `app_info_list[]`，每项含 `env_id` 与探针计数

#### Scenario: business_id 与 region 回落配置默认值
- **WHEN** 未提供 `business_id` / `region`
- **THEN** 系统 SHALL 分别回落到 `huaweicloud.apm-business-id` / `huaweicloud.apm-region`，并使 header `x-business-id` 与 body `business_id` 取同一生效值

### Requirement: 输入校验

系统 SHALL 在 service 层校验 `page` MUST ≥ 1、`page_size` MUST ≥ 1（`page` 为 null 时默认 1）。不满足 MUST 返回 `INVALID_PARAM`。

#### Scenario: 分页越界
- **WHEN** `page` < 1 或 `page_size` < 1
- **THEN** 系统 SHALL 返回 `INVALID_PARAM`

### Requirement: SearchApplication 无损投影

响应 DTO `ApmSearchApplicationResponse` SHALL 无损覆盖 3 字段（`app_info_list` / `app_total_count` / `app_info_map`，其中 `app_info_map` key 为组件名，照 SDK 原样保留、不与 list 去重）。`ApmAppInfo` SHALL 覆盖 SDK `AppInfo` 全部 7 字段（`env_name` / `env_id` / `app_name` / `app_id` / `online_count` / `disable_count` / `offline_count`）。

#### Scenario: 契约测试全字段断言
- **GIVEN** 真实 SDK 样本 `sdk-samples/apm/search-application-response.json`（list 两条 + map 一条 + total）
- **WHEN** 反序列化并经 adapter 映射
- **THEN** 响应 3 字段与 `ApmAppInfo` 7 字段 SHALL 逐一断言通过；请求侧断言 header/body 的 business_id 同源、region/page 装配正确
