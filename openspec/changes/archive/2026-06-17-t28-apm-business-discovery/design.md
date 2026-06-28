## Context

存量回填。原始 spec `docs/specs/tools/list_apm_business.md`、`docs/specs/tools/search_apm_application.md`，任务卡 `docs/tasks/T28-apm-business-discovery.md`。本文承载主 spec 放不下的 SDK 映射与非功能要求。

## Goals / Non-Goals

**Goals:**
- 让 `business_id` / `env_id` 一律运行时发现，杜绝跨 env 复用或臆造。

**Non-Goals:**
- 应用 / 环境的写操作（创建 / 修改）。
- 探针管理。

## Decisions

### list_apm_business — SDK 映射

- SDK：`com.huaweicloud.sdk.apm.v1.AomClient`/`ApmClient` `listBusiness(ListBusinessRequest)`，`GET /v1/apm2/openapi/cmdb/business/get-business-list`，SDK v3.1.x。无入参。
- `ListBusinessResponse.business_nodes` → `List<ApmBusinessNode>`；`BusinessNodeModel`→`ApmBusinessNode`（9 字段）。
- 关键：JSON 键 `default` 是 Java 关键字 → DTO 组件名 `defaultFlag` + `@JsonProperty("default")`；与 `is_default` 并存。`gmt_create`/`gmt_modify` 用 `LocalDate`（贴齐 SDK，需 JavaTimeModule）。
- 缓存：`apm.discovery-cache`（TTL 1d），无参缓存 key 用 Spring SimpleKey，空/null 不写。

### search_apm_application — SDK 映射

- SDK：`searchApplication(SearchApplicationRequest)`，`POST /v1/apm2/openapi/apm-service/app-mgr/search`。
- 请求：`business_id`→header `x-business-id` + body `business_id`（同一生效值，null 回落 `apm-business-id` 配置）；`region`→body（null 回落 `apm-region`）；`page`(默认1)/`page_size`/`keyword`→body。
- 响应：`SearchApplicationResponse`（`app_info_list`/`app_total_count`/`app_info_map`，map 原样保留不去重）；`AppInfo`→`ApmAppInfo`（7 字段，含探针三计数）。
- **不缓存**：探针计数随上下线变化，是运行态信号。

## Risks / Trade-offs

- **限流**：两者复用 `apm-readonly`，API 名 `apm.listBusiness` / `apm.searchApplication`。
- **必填兜底**：上游要求 `x-business-id`/`region` 必填，本服务以配置默认值兜底、工具层标记可选；两者均无生效值时由上游报错。
