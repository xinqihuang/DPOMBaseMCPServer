# Tool Spec: `list_apm_business`

> 状态: Ready · API 版本: **APM v1 `ListBusiness`（GET /v1/apm2/openapi/cmdb/business/get-business-list）** · SDK: `huaweicloud-sdk-apm:3.1.177`
> 权威 schema 来源：SDK sources jar `com.huaweicloud.sdk.apm.v1.model.{ListBusinessRequest, ListBusinessResponse, BusinessNodeModel}`（已逐字段核对）。
> 官方文档：https://support.huaweicloud.com/intl/zh-cn/api-apm2/apm_api_1001.html（查询应用列表）

## 1. 定位

APM CMDB 发现链的第 0 步：列出当前租户在 APM 中注册的全部应用（business）。
输出的 `id` 即后续所有 APM 工具需要的 `business_id`：

```
list_apm_business → business_id → search_apm_application → env_id
  → show_env_monitor_items → show_apm_monitor_item_view_config → show_apm_trend
```

## 2. 请求映射

无入参（SDK `ListBusinessRequest` 为空对象，鉴权走 SDK 凭据）。

## 3. 响应映射（SDK → DTO，§4.1 无损）

`ListBusinessResponse` → `ApmListBusinessResponse`：`business_nodes` → List\<ApmBusinessNode\>。

`BusinessNodeModel` → `ApmBusinessNode`（**9 个字段全量**；JSON 键 `default` 为 Java 关键字，
DTO 组件名取 `defaultFlag` + `@JsonProperty("default")`，与 `is_default` 是两个独立字段，都保留）：

| DTO 字段 (snake_case) | SDK @JsonProperty | 类型 |
|---|---|---|
| `default`（组件名 defaultFlag） | `default` | Boolean |
| `display_name` | `display_name` | String |
| `eps_id` | `eps_id` | String |
| `gmt_create` | `gmt_create` | **LocalDate**（贴齐 SDK，非 String 臆测） |
| `gmt_modify` | `gmt_modify` | LocalDate |
| `id` | `id` | Long（即 business_id） |
| `inner_domain_id` | `inner_domain_id` | Integer |
| `is_default` | `is_default` | Boolean |
| `name` | `name` | String |

## 4. 缓存

应用列表是稳定目录 → 接入 APM discovery 缓存（`apm.discovery-cache`，TTL 默认 1d）。
无参方法，缓存 key 用 Spring 默认 SimpleKey；空列表/null 不写缓存。

## 5. 错误与校验

无本地校验；上游异常经 `SdkExceptionMapper` 映射；限流实例 `apm-readonly`，API 名 `apm.listBusiness`。

## 6. 测试策略

- TC：`sdk-samples/apm/list-business-response.json`（一条全字段 + 一条最小字段）经 SDK 反序列化
  → adapter 映射，断言 9 字段全覆盖（注意 LocalDate 解析需 JavaTimeModule）。
- UT（service）：缓存命中（同参二次 `times(1)`）、空列表不缓存。
- UT（tool）：成功透传、`SmartomException` → `ErrorResponse`。
