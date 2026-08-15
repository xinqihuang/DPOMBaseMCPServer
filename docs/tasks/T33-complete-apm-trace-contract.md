# T33 — 完善 APM Trace 查询契约

## 目标

让 DPOMAgent 能从单次 `query_traces` 响应中明确获知当前页、页大小和是否存在下一页，同时避免服务端自动拉取大量数据。

## 验收标准

- 响应稳定返回 `total`、`spans`、`page`、`pageSize`、`hasMore`。
- `total` 缺失时 `hasMore=null`，不做推测。
- 不改变上游 span 顺序，不自动翻页。
- OpenSpec strict validation 与 `mvn clean verify` 通过。
- 默认运行态仍不暴露 CES 写工具。
