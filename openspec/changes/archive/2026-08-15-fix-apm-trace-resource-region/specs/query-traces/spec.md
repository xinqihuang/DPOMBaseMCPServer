## ADDED Requirements

### Requirement: business_id 双位置装配

系统 SHALL 将有效 `businessId` 同时写入请求头 `x-business-id` 与请求体 `biz_id`，两处 MUST 使用同一个值。

#### Scenario: 查询指定应用的 span
- **WHEN** Agent 传入 `businessId=111092`
- **THEN** 请求头 `x-business-id` SHALL 为 `111092`
- **AND** 请求体 `biz_id` SHALL 为 `111092`

## MODIFIED Requirements

### Requirement: APM 端点区域与资源区域分离

系统 SHALL 使用 `huaweicloud.apm-region` 选择 APM SDK 端点，但 SHALL 使用工具参数 `region` 填充 `TraceSearchParam.region`；工具参数为空时 SHALL 回落到 `huaweicloud.region`。系统 MUST NOT 将 APM 端点区域作为被查询资源区域。

#### Scenario: 查询非 APM 端点区域中的应用
- **GIVEN** APM SDK 端点为 `cn-north-4`，被查询应用位于 `cn-north-9`
- **WHEN** Agent 传入 `region=cn-north-9` 或主资源区域配置为 `cn-north-9`
- **THEN** `TraceSearchParam.region` SHALL 为 `cn-north-9`
- **AND** APM SDK 客户端 SHALL 继续连接 `cn-north-4` 端点
