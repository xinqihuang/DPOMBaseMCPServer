# Tool Spec: `search_apm_application`

> 状态: Ready · API 版本: **APM v1 `SearchApplication`（POST /v1/apm2/openapi/apm-service/app-mgr/search）** · SDK: `huaweicloud-sdk-apm:3.1.177`
> 权威 schema 来源：SDK sources jar `com.huaweicloud.sdk.apm.v1.model.{SearchApplicationRequest, AppSearchParam, SearchApplicationResponse, AppInfo}`（已逐字段核对）。
> 官方文档：https://support.huaweicloud.com/intl/zh-cn/api-apm2/apm_api_1004.html（对指定区域下的组件和环境及其探针情况进行搜索）

## 1. 定位

APM CMDB 发现链的第 1 步：列出指定应用（business）下的组件（app）与环境（env），
含每个环境的探针在线/停止/离线计数。输出的 `env_id` 喂给 `show_env_monitor_items`（T23 链）。
探针计数是**运行态信号**（随探针上下线变化），因此本工具**不缓存**。

## 2. 请求映射（工具入参 → SDK）

| 工具入参 (snake_case) | 类型 | 必填 | SDK 目标 | 说明 |
|---|---|---|---|---|
| `business_id` | Long | 否* | header `x-business-id` + body `business_id` | 应用 id，取自 `list_apm_business`；为 null 时回落到 `huaweicloud.apm-business-id` 配置（与 T23 工具同款约定）。API 层两处取值相同，adapter 用同一生效值填充 |
| `region` | String | 否* | body `region` | 区域名称（如 `cn-north-4`）；为 null 时回落到 `huaweicloud.apm-region` 配置 |
| `page` | Integer | 否 | body `page` | 页码（API 必填）；为 null 时默认 1（DTO 紧凑构造器），≥1 |
| `page_size` | Integer | 否 | body `page_size` | 每页条数，≥1 |
| `keyword` | String | 否 | body `keyword` | 组件/环境名称关键字过滤 |

> *API 文档中 `x-business-id`/`business_id`/`region` 为必填；本服务以配置默认值兜底，
> 故工具层标记可选。两者均无生效值时由上游报错。

## 3. 响应映射（SDK → DTO，§4.1 无损）

`SearchApplicationResponse` → `ApmSearchApplicationResponse`（3 字段全量）：

| DTO 字段 | SDK @JsonProperty | 类型 |
|---|---|---|
| `app_info_list` | `app_info_list` | List\<ApmAppInfo\> |
| `app_total_count` | `app_total_count` | Integer |
| `app_info_map` | `app_info_map` | Map\<String, ApmAppInfo\>（key 为组件名，照 SDK 原样保留，不与 list 去重） |

`AppInfo` → `ApmAppInfo`（**7 个字段全量**）：

| DTO 字段 | SDK @JsonProperty | 类型 |
|---|---|---|
| `env_name` | `env_name` | String |
| `env_id` | `env_id` | Long（喂给 show_env_monitor_items） |
| `app_name` | `app_name` | String |
| `app_id` | `app_id` | Long |
| `online_count` | `online_count` | Integer（在线探针数） |
| `disable_count` | `disable_count` | Integer（手动停止探针数） |
| `offline_count` | `offline_count` | Integer（离线探针数） |

## 4. 错误与校验

- `page` < 1、`page_size` < 1 → `INVALID_PARAM`（service 层）。
- 上游异常经 `SdkExceptionMapper` 映射；限流实例 `apm-readonly`，API 名 `apm.searchApplication`。

## 5. 测试策略

- TC：`sdk-samples/apm/search-application-response.json`（list 两条 + map 一条 + total）经 SDK
  反序列化 → adapter 映射，断言 3+7 字段全覆盖；请求侧断言 header 与 body 的 business_id
  同源、region/page 装配正确、null business_id 回落配置默认值。
- UT（service）：page/page_size 边界 → `INVALID_PARAM`；合法请求透传。
- UT（tool）：成功透传、`SmartomException` → `ErrorResponse`。
