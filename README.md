# DPOMBaseMCPServer

> Huawei Cloud monitoring MCP server — exposing AOM/APM/CES read-only APIs as MCP tools for an intelligent ops agent.

## 项目状态

🚧 Bootstrapping — 当前处于骨架搭建阶段，第一个业务 tool `list_ces_metrics` 在 T05 任务卡。

## 快速开始

### 给开发者

1. 读 [`CLAUDE.md`](./CLAUDE.md) —— 项目约束 / 编码规范 / AI Coding 工作流
2. 读 [`docs/architecture.md`](./docs/architecture.md) —— 架构基线
3. 在 [`docs/tasks/`](./docs/tasks/) 选一个任务，用 IDE AI 跑

### 给 AI（Claude / Cursor / Copilot）

打开本仓库时，自动读 `CLAUDE.md`（根目录）和 `.cursor/rules/*.mdc`。请遵守其中约束，特别是：

- **不引入** Lombok / WebFlux / Guava
- **不泄漏** Huawei Cloud SDK 类型到 `adapter.*` 之外
- 测试用例 1:1 对应 spec 中的 UT-XX / TC-XX

## 仓库结构

```
.
├── CLAUDE.md                          ← AI Coding 总入口
├── .cursor/rules/                     ← Cursor 规则镜像
├── docs/
│   ├── architecture.md
│   ├── specs/tools/                   ← 每个 tool 一份 spec
│   ├── decisions/                     ← 架构决策记录 (ADR)
│   └── tasks/                         ← AI 任务卡
├── .cloudbuild/build.yml              ← CodeArts Pipeline
├── helm/dpom-mcp-server/              ← Helm Chart
├── scripts/smoke/                     ← 部署后冒烟脚本
└── agentic-*/                         ← 六个 Maven 子模块 (agentic-common, agentic-adapter-ces, ...)
```

## 技术栈

| 项 | 选型 |
|---|---|
| 语言 | JDK 21 |
| 框架 | Spring Boot 3.4 + Spring AI 1.0.4 |
| 协议 | MCP (SSE transport, WebMvc) |
| SDK | huaweicloud-sdk-java-v3 3.1.196 |
| 容错 | Resilience4j |
| 部署 | CCE + Helm + SWR |

## 部署

详见 [T02 任务卡](./docs/tasks/T02-cicd-deploy.md)。

镜像 tag 规则：`<版本>_<构建时间>`，如 `2.0.0_20260520171836`。

完整镜像路径：

```
swr.cn-north-9.myhuaweicloud.com/powercloud/DPOMBaseMCPService:<version>_<timestamp>
```

## 当前可用 MCP Tools

| Tool | 状态 | Spec |
|---|---|---|
| `hello_world` | T01 占位 | — |
| `list_ces_metrics` | T05 in progress | [spec](./docs/specs/tools/list_ces_metrics.md) |

## License

Internal use — Huawei SmartOM.
