# T28 — list_apm_business + search_apm_application：APM CMDB 发现链前端

> 状态: **Done** · 估时: 0.5d · 依赖: spec `list_apm_business.md` / `search_apm_application.md`、T23（链路后段 + APM discovery 缓存基建）、§4.1/§4.3 · 官方文档 apm_api_1001 / apm_api_1004（语义参考；字段以 SDK sources jar 为准）

## 背景

T23 链（show_env_monitor_items → view_config → show_apm_trend）的入口是 `env_id`，
但 Agent 目前拿不到 `business_id` 与 `env_id`——它们只能来自告警 payload 或人工输入。
补两个 CMDB 发现工具后链路完整自闭环：

```
list_apm_business（应用列表，business_id）
  → search_apm_application（组件/环境 + 探针计数，env_id）
    → show_env_monitor_items → show_apm_monitor_item_view_config → show_apm_trend
```

## 范围

**做**：
1. spec 两份（已完成，字段表来自 SDK sources jar 逐字段核对）。
2. DTO（apm dto 包，§4.1 无损）：
   - `ApmBusinessNode`（9 字段；JSON 键 `default` 是 Java 关键字 → 组件名 `defaultFlag` +
     `@JsonProperty("default")`；`gmt_create/gmt_modify` 贴齐 SDK 用 `LocalDate`）
   - `ApmListBusinessResponse`、`ApmAppInfo`（7 字段）、`ApmSearchApplicationResponse`（3 字段）
   - `ApmSearchApplicationRequest`（businessId/region/page/pageSize/keyword；紧凑构造器 page 默认 1）
3. `ApmDiscoveryAdapter` 增加 `listBusiness()` / `searchApplication(req)`（同接口域：CMDB 发现）；
   impl 沿用既有约定：`apm-readonly` 限流、businessId 回落 `huaweicloud.apm-business-id`、
   region 回落 `huaweicloud.apm-region`；API 名 `apm.listBusiness` / `apm.searchApplication`。
4. `ApmDiscoveryService` 增加两方法：`listBusiness()` 带 `@Cacheable`
   （新 cache 名 `apm-business-list`，并入 APM 默认 spec，TTL 1d）；`searchApplication`
   校验 page/page_size ≥1，**不缓存**（探针计数是运行态信号）。
5. 新增 `ApmBusinessTool#list_apm_business`、`ApmApplicationTool#search_apm_application`
   + 注册进 `McpServerConfig`；描述写明链路顺序与 business_id 来源（禁止编造）。
6. 测试：spec §测试策略全部用例（两份契约 TC + 样本 JSON、service 缓存/校验 UT、tool UT）。

**不做**：
- ❌ 不做 CMDB 写操作 / 环境标签管理（只读范围外）
- ❌ `search_apm_application` 不缓存（探针在线数是实时信号）
- ❌ 不动 T23 既有三工具（链路描述指向由新工具承担）

## 验收标准

- [x] `ApmBusinessNode` 覆盖 SDK `BusinessNodeModel` 全部 9 字段（含 `default` 与 `is_default`
      两个独立布尔），`ApmAppInfo` 覆盖 `AppInfo` 全部 7 字段；契约测试漂移即 fail
- [x] `search_apm_application` 请求侧：header 与 body 的 business_id 同源；null 回落配置默认值
- [x] `list_apm_business` 命中缓存（同参二次 `times(1)`）；空列表不缓存
- [x] page/page_size 非法 → `INVALID_PARAM`
- [x] mcp 不直接 import huaweicloud SDK；依赖方向不破
- [x] 迭代 `mvn -o -q -pl agentic-mcp -am test`；收尾全量 `mvn verify` 一次通过；Checkstyle 0

## AI 易错点提醒

1. `BusinessNodeModel` 的 `default` 与 `is_default` 是**两个**字段，都要保留，别合并。
2. `gmt_create/gmt_modify` 是 SDK `LocalDate`，别写成 String/Long；契约测试的 ObjectMapper
   要 `findAndRegisterModules()` 才能解析。
3. `x-business-id`（header）与 `business_id`（body）取**同一生效值**（含配置回落），别只填一处。
4. `app_info_map` 与 `app_info_list` 内容可能重复，照 SDK 原样两个都保留（§4.1 无损），别去重。
5. 限流实例用既有 `apm-readonly`；businessId/region 回落逻辑放 adapter（与 T23 一致），别散到 service。
6. 与真实 SDK 冲突 → 停下来问（CLAUDE.md §5.1）。

## 完成后

PR：`feat(T28): list_apm_business + search_apm_application — APM CMDB discovery chain front`
