## MODIFIED Requirements

### Requirement: span 响应投影

响应 DTO `ApmQueryTracesResponse` SHALL 返回 `{total, spans, page, pageSize, hasMore}`。`ApmSpan` SHALL 从 SDK `ClientSpanInfo` 无损投影，span 顺序 SHALL 与上游一致且 `spans` SHALL 始终非 null。`page` 与 `pageSize` SHALL 回显实际请求分页参数。当上游 `total` 非 null 时，系统 SHALL 以 `page * pageSize < total` 计算 `hasMore`；当上游 `total` 为 null 时，`hasMore` SHALL 为 null，系统 MUST NOT 猜测或自动拉取下一页。

#### Scenario: 仍有下一页
- **GIVEN** 请求 `page=2`、`pageSize=50` 且上游返回 `total=101`
- **WHEN** adapter 投影响应
- **THEN** 响应 SHALL 包含 `page=2`、`pageSize=50`、`hasMore=true`
- **AND** span 顺序 SHALL 与上游一致

#### Scenario: 已到最后一页
- **GIVEN** 请求 `page=1`、`pageSize=50` 且上游返回 `total=1`
- **WHEN** adapter 投影响应
- **THEN** `hasMore` SHALL 为 false

#### Scenario: total 可空透传
- **WHEN** 上游 `getTotal()` 返回 null
- **THEN** `total` 与 `hasMore` SHALL 均为 null
- **AND** 系统 MUST NOT 自动请求下一页

#### Scenario: tags 兜底非 null
- **GIVEN** 上游某 span 的 `getTags()` 返回 null
- **WHEN** adapter 投影该 span
- **THEN** `ApmSpan.tags` SHALL 为空 Map（非 null）
