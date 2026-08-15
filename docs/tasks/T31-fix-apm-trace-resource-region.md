# T31 — 修复 APM trace 资源区域装配

## 范围

修复 `query_traces` 将 APM SDK 接入端点区域误作被查询应用资源区域的问题。工具新增可选 `region`，优先使用告警或应用发现结果中的资源区域，缺省时回落到 `huaweicloud.region`。
同时修复接口必填应用 ID 只写请求头、未写请求体 `biz_id` 的问题。

## 不在范围

- 不修改 APM 告警或监控数据。
- 不新增写工具。
- 不改变 APM SDK 接入端点的选择方式。

## 验收

- `cn-north-4` APM 端点可以查询 `cn-north-9` 应用调用链。
- 工具 schema 明确区分 endpoint region 与 resource region。
- 单元测试、契约测试及 `mvn clean verify` 通过。
- 使用真实只读调用不再返回由错误 region 导致的 `invalid parameter`。
