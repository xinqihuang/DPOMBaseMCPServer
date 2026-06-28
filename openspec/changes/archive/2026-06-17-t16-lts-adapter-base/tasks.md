> 存量回填：以下任务已于早期 commit 交付（原任务卡 `docs/tasks/T16-lts-adapter-base.md`，状态 In Progress→Done）。已交付项勾选 `[x]`；本期未交付的遗留项列在末尾未勾选。

## 1. 工程结构与依赖

- [x] 1.1 新建 Maven 子模块 `agentic-adapter-lts`（`pom.xml` / `src/main` / `src/test`），parent 为 `agentic-adapter`，依赖 `huaweicloud-sdk-lts` + `agentic-common`
- [x] 1.2 根 `pom.xml` 的 `dependencyManagement` 新增 `huaweicloud-sdk-lts`（共用 `${huaweicloud-sdk.version}` = 3.1.177，不 override）
- [x] 1.3 聚合父 `agentic-adapter/pom.xml` 注册 `<module>agentic-adapter-lts</module>`

## 2. 配置层

- [x] 2.1 `HuaweiCloudProperties` 新增 `ltsRegion` 字段 + getter/setter + Javadoc（与主 region 解耦，参考 `apmRegion`）
- [x] 2.2 `LtsClientConfig`（`@Configuration`）注入单例 `LtsClient` Bean，复用 `HuaweiCloudClientFactory.credentialsWithProjectId(...)` + `defaultHttpConfig()` + `LtsRegion.valueOf(...)`
- [x] 2.3 `application.yml` 新增 `huaweicloud.lts-region: ${HUAWEICLOUD_LTS_REGION:cn-north-9}` 与 `lts-readonly` RateLimiter（10 QPS）

## 3. Adapter 接口与 DTO

- [x] 3.1 `LtsLogAdapter` 接口（`listLogs` / `listLogContext` 两个只读方法）
- [x] 3.2 DTO record：`LtsListLogsRequest` / `LtsListLogsResponse` / `LtsListLogContextRequest` / `LtsListLogContextResponse` / `LtsLogEntry`（SDK `LogContents` 重命名）
- [x] 3.3 SDK 类型不外泄（adapter 边界隔离，`grep -r "com.huaweicloud.sdk.lts" agentic-monitoring agentic-mcp` 无结果）

## 4. Adapter 实现

- [x] 4.1 `LtsLogAdapterImpl`（`@Component`）经 `HuaweiCloudInvocation.execute(...)` 走限流 / 重试 / 异常映射通道
- [x] 4.2 `toSdk*` 转换：`start_time`/`end_time` 用 `String.valueOf(long)`；`cursorTime` → `body.setTime(...)`（`__time__`）；`searchType` 用 `SearchTypeEnum.fromValue(...)`
- [x] 4.3 `toDto*` 转换：`analysisLogs` 保 `List<Object>` 透传；`LtsLogEntry` 仅 content/lineNum/labels
- [x] 4.4 SDK 异常 → `ErrorCode` 映射（429→`UPSTREAM_THROTTLED` / 401→`UPSTREAM_AUTH_FAILED` / 5xx→`UPSTREAM_ERROR` / Timeout→`TIMEOUT`），透传 `upstreamTraceId`

## 5. 测试

- [x] 5.1 `LtsClientConfigTest`：Spring context 启动 + `LtsClient` Bean 非 null
- [x] 5.2 `LtsLogAdapterImplTest` listLogs：UT-01 全合法参数字段对齐 / UT-02 429→throttled / UT-03 401→auth / UT-04 5xx→error / UT-05 Timeout / UT-06 analysisLogs 透传
- [x] 5.3 `LtsLogAdapterImplTest` listLogContext：UT-07 合法参数字段对齐 / UT-08 429→throttled / UT-09 isQueryComplete=false 透传

## 6. 遗留项（本期未交付）

- [ ] 6.1 Contract Test（SDK 反射 / JSON 反序列化，27 字段全断言风格）+ sdk-samples 样本
- [ ] 6.2 部署后冒烟脚本 `scripts/smoke/`
- [ ] 6.3 健康检查里加 LTS connectivity probe
- [ ] 6.4 `agentic-monitoring` LTS 业务服务 + MCP Tool 注册（T17–T18）
- [ ] 6.5 结构化日志查询（`/struct-content/query`：`listStructuredLogsWithTimeRange` / `listQueryStructuredLogs`）
