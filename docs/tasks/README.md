# Tasks Overview

本目录的每份 `T<NN>-<name>.md` 都是一个"喂给 AI 的可执行单元"。开发者用 IDE AI（Claude Code / Cursor / Copilot 等）打开任务卡，AI 应：

1. 先读 `CLAUDE.md`（项目根）
2. 再读本任务卡引用的 spec / ADR
3. 按"产物清单"生成文件
4. 按"验收标准"自查
5. 提 PR

## 任务总览

| ID | 名称 | 状态 | 估时 | 依赖 |
|---|---|---|---|---|
| [T01](T01-bootstrap.md) | 项目骨架 + hello-world tool | Ready | 0.5d | — |
| [T02](T02-cicd-deploy.md) | CI/CD + Dockerfile + Helm Chart | Ready | 0.5d | T01 |
| [T03](T03-common.md) | common 模块（错误码 / 异常 / 限流重试 / 健康检查） | Ready | 0.5d | T01 |
| [T04](T04-ces-adapter-base.md) | CES Adapter 基座（CesClient + 接口） | Ready | 0.3d | T03 |
| [T05](T05-list-ces-metrics.md) | 实现 list_ces_metrics tool | Ready | 1d | T01-T04 |
| [T06](T06-list-aom-metrics.md) | 实现 list_aom_metrics tool | Draft | 1.5d | T01-T03 |
| [T07](T07-query-ces-metric-data.md) | 实现 query_ces_metric_data tool | Done（`4c346d6`） | 1d | T05 |
| [T08](T08-query-aom-metric-data.md) | 实现 query_aom_metric_data tool | Done（`4c346d6`） | 1d | T06 |
| [T09](T09-list-ces-alarms.md) | 实现 list_alarms tool（CES 告警历史） | Done（`4c346d6`） | 0.5d | T05 |
| [T10](T10-query-apm-traces.md) | 实现 query_traces tool（APM 调用链搜索） | Done（`4c346d6`） | 1d | T03 |
| [T11](T11-get-apm-topology.md) | 实现 get_service_topology tool（APM 拓扑） | Done（`4c346d6`） | 0.5d | T10 |
| [T12](T12-correlate-incident.md) | 实现 correlate_incident（跨组件编排） | Done（`4c346d6`） | 1.5d | T07-T11, T13 |
| [T13](T13-query-aom-logs.md) | 实现 query_logs tool（AOM 日志查询） | Done（`4c346d6`） | 1d | T03 |
| [T14](T14-batch-query-ces-metric-data.md) | 实现 batch_query_ces_metric_data + CES 参数枚举目录 | Done（`ce6fd6c`） | 1d | T04, T07 |
| [T15](T15-ces-notification-masks.md) | CES 告警屏蔽三件套（create / delete / list） | Done（`7bf5907`） | 1d | T04 |
| [T16](T16-lts-adapter-base.md) | LTS Adapter 基座 + listLogs / listLogContext | Done（`abd1a8d`） | 1d | T03 |
| [T17](T17-query-lts-logs.md) | 实现 query_lts_logs tool（LTS 日志搜索） | Done | 0.5d | T16 |

> 注：T07–T13 与 T15 的 spec/task 卡是 2026-06-02 回填的，原始实现在更早的 commit 中（见各任务卡引用的 hash）。回填内容反映"已交付现状"而非"理想 DoD"，遗留测试 / 冒烟 / 指标看板等项目列在各任务卡的"不做（本期未交付）"段落，并汇总在下文「后续任务 / 遗留项」。

## 推荐执行顺序

- **串行路径**：T01 → T03 → T04 → T05
- **并行路径**：T02 可以和 T03 并行
- **关键里程碑**：T05 完成 = 第一个业务 tool 上线 ready，可以部署到贵阳验证

## 后续任务 / 遗留项

### 测试覆盖补齐

- **T07** Service / Adapter UT、Contract Test、冒烟脚本（已有 `CesMetricDataToolTest` 共 7 条）
- **T08** 全部 UT（Tool / Service / Adapter）+ TC + 冒烟脚本（当前**零测试覆盖**）
- **T09 / T10 / T11 / T12 / T13** 全部 UT + TC + 冒烟脚本（当前**零测试覆盖**）
- **T14** Service / Adapter UT、Contract Test、冒烟脚本（已有 `CesMetricDataToolTest` + `CesBatchMetricDataToolTest` 共 14 条）
- **T15** 全部 UT（write tool 风险最高）+ TC + 冒烟脚本（当前**零测试覆盖**）

### 架构遗留 / 待 ADR

- **MCP `annotations`（readOnlyHint / destructiveHint / idempotentHint）未实际设置**：Spring AI 1.0.4 `@Tool` 注解不暴露这些字段；spec 中描述的 annotation 当前仅是"意图"，未传到 MCP 客户端。涉及 `delete_notification_masks` 等写工具的语义安全。
- **AOM 入参仍用 `Set<Integer>` / `Set<String>` 校验 period / statistics**：与 CES 的 `CesMetricFilter` / `CesMetricPeriod` 枚举不对称（见 ADR-004）。是否对 AOM 做同样治理待决。
- **`query_logs` 复用 `AomMetricsAdapterImpl`**：类名"Metrics"已带歧义；如要拆分到独立 `AomLogAdapter`，开新任务。
- **`correlate_incident` 限流计费**：单次调用扇出到 4 个下游、消耗 3 个 RateLimiter 域、APM 域消耗 2 个 permit；流量增长时需要单独配额。
- **CES 命名空间 / 指标枚举目录扩展**（按 ADR-004 宽容目录策略增量补 RDS / DDS / EVS / OBS 等）

### 文档 / 产品遗留

- 每个 tool 的 Micrometer 指标看板配置
- 每个 tool 的 README 使用示例
- `create_notification_mask` 客户端去重 / 同名预检
- `delete_notification_masks` 部分删除（输入 N 个 ID，上游只删除 M 个）的明确告知 UX

## 任务卡的写法约定

1. **状态**：Draft / Ready / In Progress / Done / Blocked
2. **依赖**：必须列清前置任务（也叫"上游"）
3. **范围**：必须明确 **做** 和 **不做**，避免 AI 越界
4. **产物清单**：列出所有要交付的文件路径
5. **关键技术要求**：含可参考的代码片段（不是完整代码，是骨架）
6. **AI 易错点**：项目里踩过的坑、AI 高频写错的地方
7. **验收标准**：可执行的 checklist
