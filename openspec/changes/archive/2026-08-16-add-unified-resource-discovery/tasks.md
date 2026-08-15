# Tasks

## 1. DTO 与编排

- [x] 1.1 ResourceIdentifier / ResourceContext / ResourceCandidate / MissingCapability / MatchType / DiscoveryRequest 等 DTO（provenance 分离、matchType、扩展锚点）
- [x] 1.2 ResourceDiscoveryService（USER_PROVIDED 锚点保留 + 一致性校验 + 动态缺口 + 候选不静默）

## 2. MCP 工具

- [x] 2.1 discover_resource_context 工具（只读，至少一个锚点，含 alarmId/traceId/logGroupId/logStreamId）
- [x] 2.2 resolve_resource_candidates 工具（只读，matchType 候选，不重复提示已提供字段）

## 3. 分页遍历

- [x] 3.1 search_apm_application 逐页遍历全部页（appTotalCount 仅作提示），pageSize=100 + MAX_PAGES=20 安全上限
- [x] 3.2 防页码停滞：空页/短页/MAX_PAGES 任一即终止，appTotalCount 仅作提示

## 4. 同名标识冲突收敛

- [x] 4.1 同值去重为单一规范值（优先上游验证来源）
- [x] 4.2 异值标记 ambiguous + conflict missing capability，保留双方 provenance

## 5. 测试与验收

- [x] 5.1 单元测试：无锚点拒绝、instanceId 保留、appId 不冒充 instanceId、envId 收敛、动态缺口、provenance 分离、多候选不静默、APM alarm 锚点保留、分页（目标第2页/无匹配/多页多匹配/总数偏小/元数据异常/安全上限）、冲突收敛（envId/appName/monitorItemId 同值去重；LTS log_group_id 异值冲突；LTS log_stream_id 同值+异值）
- [x] 5.2 mvn clean verify + OpenSpec strict validate
- [x] 5.3 真实只读 smoke test（仅验证真实完成能力）+ 中文验收报告
