# 团队 AI Coding 使用指南

> 给项目成员看的"如何用 AI 写 DPOMBaseMCPServer 代码"指南。

## 总原则

**AI 是结对编程的伙伴，不是代码喷射器。**

- 让 AI 干**结构清晰、有 spec 兜底**的活
- 你来做**理解需求、设计取舍、Review 把关**
- 不要让 AI 写它不该写的（比如安全 / 认证 / 业务策略类代码）

## 标准流程：从任务卡到 PR

### Step 1 — 拿到任务卡

进入 `docs/tasks/` 找到你认领的任务，如 `T05-list-ces-metrics.md`。

### Step 2 — 自己先读一遍

至少读这三份：

1. 任务卡本身
2. `CLAUDE.md`（项目约束，你也要懂，不只是 AI）
3. 任务卡引用的 spec / ADR

读完心里要能回答：
- 我要交付什么文件？
- 我的代码会怎么被测？
- 有什么坑我得注意？

读不懂任何一项 = 找作者澄清，不要硬上。

### Step 3 — 给 AI 喂 prompt

#### 推荐的 prompt 模板

```
请按照以下任务卡实现：

@docs/tasks/T05-list-ces-metrics.md

相关上下文：
- 项目规范：@CLAUDE.md
- Spec：@docs/specs/tools/list_ces_metrics.md
- 架构图：@docs/architecture.md
- 上游 common 模块：@agentic-common/

请按"产物清单"中的顺序逐个创建文件。
每创建一个文件后简短说明 1 句话：这个文件解决了什么。
遇到任何 spec 没明确的点，停下来问我，不要凭印象决定。
```

#### Cursor 用户

`@` 引用会自动把这些文件塞进上下文。

#### Claude Code 用户

直接拖入文件或用 `/include` 命令。

#### Copilot 用户

用 Copilot Chat 的 `@workspace` 引用。

### Step 4 — Review AI 的产出

不要按 Accept 一键全收。逐个文件看：

- **格式**：行宽 / 缩进 / 大括号 / import 顺序符合 `CLAUDE.md` §3 吗？
- **依赖**：有没有偷偷引入 Lombok / WebFlux？
- **SDK 边界**：有没有把 SDK 类型泄漏到 adapter 之外？
- **null 处理**：方法签名有没有 `null` 参数 / 返回？
- **异常**：catch 的是具体类型还是 `Exception`？
- **日志**：用占位符 `{}` 还是字符串拼接？敏感字段没打吗？
- **测试覆盖**：spec 列的 UT-XX/TC-XX 是不是每个都有对应方法？

### Step 5 — 跑测试 + checkstyle

```bash
mvn clean install
mvn checkstyle:check
```

任意一个失败 = 不要提 PR。让 AI 修，或者你自己修。

### Step 6 — 自查 Checklist

照 `CLAUDE.md` §6 的清单过一遍。

### Step 7 — 提 PR

PR 描述里贴上：
- 任务卡链接
- 关键变更说明
- 测试用例对应表（哪个测试覆盖了 spec 的哪个 UT-XX）

## AI 用得好的几个习惯

### 1. 一次只给一个明确任务

❌ 不要："帮我把这个项目骨架做完，再做 list_ces_metrics"
✅ 要："按 T01 任务卡做项目骨架"，做完再："按 T05 做 list_ces_metrics"

任务越聚焦，AI 越准。

### 2. 让 AI 解释而不是直接改

遇到 AI 写错的代码：

❌ 不要："这里错了，重写"
✅ 要："这段代码为什么这样写？spec §3.2 要求 XX，你的实现 YY 是怎么对应的？"

让 AI 自己暴露推理过程，往往它自己就发现错了。

### 3. 用测试做契约

写完 spec 后，让 AI **先按 spec 写测试骨架**（每个 UT-XX 一个 `@Test` 方法，方法体先 `fail("not impl")`），review 测试结构无误后再写实现。

这样 AI 的实现是"为了让测试通过"，而不是"我觉得应该这样"。

### 4. 拒绝 AI 的"贴心扩展"

AI 经常会说"我顺便给你加了 XX 功能"。

❌ 任务卡没说要 → **删掉**
✅ 任务卡说要 → 保留

不要让范围悄悄膨胀。多出来的代码 = 多出来的维护成本。

### 5. 让 AI 读项目里的真实代码

❌ 不要："参考一下 Spring Boot 标准做法"
✅ 要："参考 @agentic-common 里的异常处理风格"

AI 看真实代码比看一般做法准得多。

## 当 AI 卡住时

### 情况 1：AI 反复改不对

往往是上下文不足。补充：
- 更具体的 spec 引用
- 现有代码片段
- 错误信息原文

### 情况 2：AI 在猜 SDK API

华为云 SDK 的方法名 / 字段名是 AI 高频出错点。让 AI **直接看 SDK 源码**：

```
请用 IDE 跳转到 com.huaweicloud.sdk.ces.v1.model.ListMetricsRequest，
告诉我它有哪些 setter 方法，我们再决定怎么调。
```

或者你自己跳转一下贴给 AI。

### 情况 3：AI 自信地写了不存在的方法

跑 `mvn compile` 编译报错。把编译错误贴给 AI。

### 情况 4：AI 写的测试通过但代码其实有问题

测试可能是 mock 自己写自己。Review 测试时关注 **mock 的边界**——mock 的是 SDK Client 这一层，不是我们自己的 service / adapter。

## 不要让 AI 写的东西

- **AK/SK 处理 / Vault 集成**：安全关键，必须人写
- **错误码定义新增**：影响所有调用方，必须设计后写
- **架构决策（ADR）**：要人想清楚再写
- **Spec 本身**：spec 是给 AI 看的输入，不能让 AI 自己写自己的输入

## 评估自己的 AI 使用

每周末花 5 分钟问自己：

- 这周 AI 帮我写的代码，有多少是一次通过 review 的？
- 多少次我让 AI 改了三次以上还没改对？
- 多少次发现 AI 写的代码在线上有问题？

如果"三次以上改不对"超过 30%，**回头看 spec 和任务卡是不是不够清晰**。AI 写得差，往往是输入差。
