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

## 推荐执行顺序

- **串行路径**：T01 → T03 → T04 → T05
- **并行路径**：T02 可以和 T03 并行
- **关键里程碑**：T05 完成 = 第一个业务 tool 上线 ready，可以部署到贵阳验证

## 后续任务（未来 sprint）

- T06: list_aom_metrics
- T07: query_ces_metric_data
- T08: query_aom_metric_data
- T09: list_alarms (CES)
- T10: query_traces (APM)
- T11: get_service_topology (APM)
- T12: correlate_incident（跨组件编排，依赖前面所有 query 类 tool）
- T13: query_logs (AOM)

每个任务卡按本目录格式编写。

## 任务卡的写法约定

1. **状态**：Draft / Ready / In Progress / Done / Blocked
2. **依赖**：必须列清前置任务（也叫"上游"）
3. **范围**：必须明确 **做** 和 **不做**，避免 AI 越界
4. **产物清单**：列出所有要交付的文件路径
5. **关键技术要求**：含可参考的代码片段（不是完整代码，是骨架）
6. **AI 易错点**：项目里踩过的坑、AI 高频写错的地方
7. **验收标准**：可执行的 checklist
