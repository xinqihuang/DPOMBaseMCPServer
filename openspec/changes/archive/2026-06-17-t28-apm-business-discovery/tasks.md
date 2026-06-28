> 存量回填：以下任务已于早期 commit 交付（原任务卡 T28，状态 Done）。已交付项勾 `[x]`。

## 1. list_apm_business

- [x] 1.1 `ApmBusinessAdapter` + impl（无入参，`apm.listBusiness`，接入 discovery 缓存）
- [x] 1.2 DTO：`ApmListBusinessResponse`、`ApmBusinessNode`（9 字段，`defaultFlag`+@JsonProperty、LocalDate）
- [x] 1.3 `ApmBusinessService`（缓存逻辑，空列表不缓存）
- [x] 1.4 `ApmBusinessTool#list_apm_business` 注册

## 2. search_apm_application

- [x] 2.1 `ApmApplicationAdapter` + impl（header/body business_id 同源、region/page 装配、`apm.searchApplication`）
- [x] 2.2 DTO：`ApmSearchApplicationResponse`（3 字段）、`ApmAppInfo`（7 字段）
- [x] 2.3 `ApmApplicationService`（page/page_size 校验，business_id/region 回落配置）
- [x] 2.4 `ApmApplicationTool#search_apm_application` 注册

## 3. 测试

- [x] 3.1 list_apm_business：契约 TC（9 字段，JavaTimeModule）、缓存命中/空不缓存 UT、tool UT
- [x] 3.2 search_apm_application：契约 TC（3+7 字段 + 请求侧 business_id 同源/回落）、page 边界 UT、tool UT

## 4. 遗留项（本期未交付）

- [ ] 4.1 两个工具的冒烟脚本
- [ ] 4.2 Micrometer 指标看板
