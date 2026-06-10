# CLAUDE.md

> 本文档是 **DPOMBaseMCPServer** 项目的 AI Coding 总入口。所有 AI 工具（Claude Code / Cursor / Copilot / 通义灵码等）在生成本项目代码前都应读完本文档并遵守其中的约定。

---

## 0. 项目身份卡

| 字段 | 值 |
|---|---|
| 服务名 | `DPOMBaseMCPServer` |
| 一句话定位 | 基于华为云 SDK，封装 AOM/APM/CES 监控能力，作为 MCP Server 提供给智能运维 Agent 使用 |
| 范围（本期 MVP） | **只读监控查询**。AOM + APM + CES 的 read-only API |
| 不在范围 | 故障恢复（写操作）—— 另起项目 |
| 部署形态 | 华为云 CCE 无状态服务，4U8G，Helm Chart 部署 |
| 仓库 | Codehub（华为内部），主分支 `master` |

---

## 1. 技术栈基线（不可随意偏离）

| 层 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | **JDK 21**（开启虚拟线程） |
| 构建 | Maven | 3.9+ |
| 框架 | Spring Boot | **3.4.x** |
| MCP | Spring AI MCP Server (WebMvc + SSE) | **1.0.4** |
| 华为云 SDK | huaweicloud-sdk-java-v3 (ces/aom/apm) | **3.1.177** |
| 容错 | Resilience4j | 2.x |
| 校验 | Jakarta Validation | Spring Boot 内置 |
| 测试 | JUnit 5 + Mockito + AssertJ | Spring Boot 内置版本 |
| 日志 | SLF4J + Logback (JSON encoder: `logstash-logback-encoder`) | — |
| 监控埋点 | Micrometer | Spring Boot 内置 |
| JSON | Jackson | Spring Boot 内置 |

**禁用清单**（AI 生成时不要引入）：
- ❌ Lombok（用 JDK 21 `record` 替代 DTO；普通 POJO 写完整 getter/setter，AI 写得稳，可读性更好）
- ❌ Spring WebFlux / Reactor（本项目用 WebMvc + 虚拟线程，不引入响应式编程）
- ❌ 任何第三方 HTTP 客户端（统一用华为云 SDK；外部调用如 Vault 用 Spring 自带 `RestClient`）
- ❌ Guava / Apache Commons Lang3（JDK 21 标准库已能覆盖；如确有需求，单点引入需 ADR 决策）

---

## 2. 模块结构

仓库布局：

```
DPOMBaseMCPServer/
├── pom.xml                                      ← parent pom
├── CLAUDE.md                                    ← 本文档
├── README.md
├── .editorconfig
├── checkstyle.xml
├── .cloudbuild/
│   └── build.yml                                ← CodeArts Pipeline
├── helm/
│   └── dpom-mcp-server/                         ← Helm Chart
├── docs/
│   ├── architecture.md
│   ├── specs/tools/                             ← 每个 tool 一份 spec
│   ├── decisions/                               ← ADR
│   └── tasks/                                   ← 任务卡 (AI 执行单元)
├── scripts/
│   └── smoke/                                   ← 部署后冒烟脚本
└── 子模块/                                       ← 7 个（含 1 个聚合父），目录名 = Maven artifactId
    ├── agentic-common/                          ← com.huawei.smartom.agentic.common
    ├── agentic-adapter/                         ← 聚合父 pom（packaging=pom，无源码）
    │   ├── agentic-adapter-ces/                 ← com.huawei.smartom.agentic.adapter.ces
    │   ├── agentic-adapter-aom/                 ← com.huawei.smartom.agentic.adapter.aom
    │   └── agentic-adapter-apm/                 ← com.huawei.smartom.agentic.adapter.apm
    ├── agentic-monitoring/                      ← com.huawei.smartom.agentic.monitoring
    └── agentic-mcp/                             ← com.huawei.smartom.agentic.mcp (Spring Boot 启动入口)
```

### 2.1 模块映射表

| Maven 目录名 / artifactId | Java 包名 | 职责 |
|---|---|---|
| `agentic-common` | `com.huawei.smartom.agentic.common.*` | 错误码、统一异常、DTO 基类、Resilience4j 配置、线程池 |
| `agentic-adapter` | —（聚合父 pom，无源码） | CES/AOM/APM 三个 adapter 子模块的 Maven 聚合容器 |
| `agentic-adapter/agentic-adapter-ces` | `com.huawei.smartom.agentic.adapter.ces.*` | CES SDK 封装 + 自定义 DTO |
| `agentic-adapter/agentic-adapter-aom` | `com.huawei.smartom.agentic.adapter.aom.*` | AOM SDK 封装 |
| `agentic-adapter/agentic-adapter-apm` | `com.huawei.smartom.agentic.adapter.apm.*` | APM SDK 封装 |
| `agentic-monitoring` | `com.huawei.smartom.agentic.monitoring.*` | 业务编排（含跨组件 correlate） |
| `agentic-mcp` | `com.huawei.smartom.agentic.mcp.*` | Spring Boot 启动类 + MCP tool 注册 + SSE endpoint |

**命名规则**：Maven artifactId 取 Java 包名的最后两截（adapter 子包取最后三截），用 `-` 连接，去掉 `com.huawei.smartom.` 前缀。

### 2.2 依赖方向（必须遵守，CI 会校验）

```
mcp ──► monitoring ──► adapter.{ces, aom, apm} ──► common
                              ▲
                              └── 仅依赖 common，不互相依赖
adapter.ces 不能依赖 adapter.aom
monitoring 不能直接依赖 common 之外的层 (必须通过 adapter 接口)
mcp 不能直接 import huaweicloud SDK
```

---

## 3. 编码规范（硬约束）

### 3.1 格式

由 `checkstyle.xml` 和 `.editorconfig` 强制：

- **行宽 120**
- **缩进 4 空格**（不用 tab）
- **大括号强制**：`if` `else` `for` `while` `do-while` 都必须带 `{}`，即使单行
- **`{` `}` 内有空格**：`if (a) { return; }`
- **块的左大括号在行尾、右大括号独占一行**：非空块的左 `{` 放在首行末尾，右 `}` 必须单独占一行（或与左 `{` 在同一行，构成真正的单行块 `if (a) { return; }`）。**不允许 `} else {` / `} catch (...) {` / `} finally {` 这类紧凑写法**，必须换行写成：
  ```java
  if (...) {
      ...
  }
  else {
      ...
  }

  try {
      ...
  }
  catch (FooException e) {
      ...
  }
  ```
- **单个方法体不超过 50 行**（不含空行与单行注释；包含签名所在行）。超过即必须拆分（提取私有方法 / 引入策略对象 / 拆 record 工厂等）。例外仅限：纯映射构造（如 record 字段 → SDK 字段一一对应，无分支逻辑），且必须在方法 Javadoc 中显式说明「机械映射，无法进一步拆分」。理由：长方法承载过多职责，单测难定位、Code Review 信号衰减、Agent 改动易引入回归。
- **不用 `import *`**：所有 import 单独列出
- **import 分组顺序**（每组之间空行）：
  ```
  1. static imports
  2. android.*
  3. androidx.*
  4. huawei.*
  5. com.huawei.*
  6. com.* (其他)
  7. 其他顶级包
  8. net.*
  9. org.*
  10. javacard.*
  11. java.*
  12. javax.*
  ```

### 3.2 命名

- 包名：全小写，本项目固定 `com.huawei.smartom.agentic.*`
- 类名：`PascalCase`，DTO 后缀 `Dto`（如 `MetricInfoDto`），不用 `VO/PO/BO` 这种区分
- record：当作值对象，名字写完整含义（如 `CesListMetricsRequest`）
- 接口：不加 `I` 前缀（写 `CesMetricsAdapter` 不写 `ICesMetricsAdapter`）
- 实现类：加 `Impl` 后缀仅当只有一个实现（如 `CesMetricsAdapterImpl`）；有多个实现时按特性命名（如 `CachingCesMetricsAdapter` / `DirectCesMetricsAdapter`）
- 常量：`UPPER_SNAKE_CASE`，定义在专门的 `Constants` 类或枚举
- **Logger 字段必须用大写 `LOG`**：固定写 `private static final Logger LOG = LoggerFactory.getLogger(X.class);`。它是 `static final`，按团队规范属于常量，Checkstyle `ConstantName` 强制 `UPPER_SNAKE_CASE`。**不允许写小写 `log`**（Spring / SLF4J 官方文档示例的小写惯例对本项目不适用）。
- 测试类：`<被测类>Test`（UT）/ `<被测类>ContractTest`（TC）

### 3.3 Null 与 Optional

- **public method 参数不接受 `null`**——必须用 `@NotNull` 标注或在方法首行校验
- **返回值不返回 `null`**——空集合返回空集合，单对象用 `Optional`
- **Optional 不当字段、不当参数、只当返回值**

### 3.4 异常

- 业务异常统一从 `com.huawei.smartom.agentic.common.exception.SmartomException` 派生
- 不要 `catch (Exception e)`——明确写出要捕获的类型
- 不要吞异常——至少 `LOG.warn` 一下（或 `LOG.error` 视情况）
- 自定义异常必须携带 `errorCode`（详见 `common.error.ErrorCode` 枚举）

### 3.5 日志

- 用 SLF4J 占位符，**不用字符串拼接**：
  - ✅ `LOG.info("Listing metrics, namespace={}, limit={}", namespace, limit)`
  - ❌ `LOG.info("Listing metrics, namespace=" + namespace)`
- 关键路径必须打 INFO，含**入参摘要**和**耗时**
- 调华为云 SDK 的地方必须在 finally 里记录 `upstreamTraceId`（华为云返回的 `X-Request-Id`）
- 敏感信息（AK/SK、token）**永远不打日志**

### 3.6 Javadoc 语言

- **类级 Javadoc 和方法级 Javadoc 一律用中文写**——描述正文、`@param` / `@return` / `@throws` 的说明文本，都用中文。
- **不翻译的部分**：
  - Javadoc 标签名本身（`@param` / `@return` / `@throws` / `@since` / `@author` 等）
  - 标识符（类名、方法名、参数名、字段名、常量名）
  - `{@code ...}` / `{@link ...}` 内部的代码引用
  - HTML 标记（`<p>` / `<ul>` / `<li>` / `<code>` 等）
  - `@since` 后的日期值、`@author` 后的作者标识
- 字段级 Javadoc、行内 `//` 注释、TODO 注释——本规则不强制，但建议同样用中文以保持一致。
- 反例：
  ```java
  /** Returns the configured region. @return the region */   // ❌ 英文
  ```
- 正例：
  ```java
  /**
   * 返回已配置的华为云 region 标识。
   *
   * @return region 标识，Spring 绑定完成后不会为 {@code null}
   */
  ```
- 理由：本项目交付给华为云内部团队，代码评审、运维 Wiki、故障排查全部用中文；Javadoc 用中文降低跨人员理解成本。

---

## 4. 关键架构决策

### 4.1 自定义 DTO 无损包裹 SDK 类型（重要）

**绝不允许** 把华为云 SDK 的 Request/Response 类泄漏到 `adapter` 之外的层。每个 SDK 调用：
adapter 把入参 DTO（我们的 record）转 SDK Request → 调 SDK → 把 SDK Response 转我们的输出 DTO → 返回上层。

**DTO 是稳定契约，但必须对 SDK 响应做无损投影**：

- **无损**：SDK 响应里的字段，DTO 必须全部覆盖。允许重命名贴齐 snake_case、允许把嵌套拆成子 record，但不允许丢字段。
- **要砍字段必须在 spec 里显式列出并写理由**，否则一律保留。「Agent 当前用不到」不是理由——只读诊断工具默认全留。
- **嵌套不拍平丢信息**：嵌套对象拆子 record（如 metric/condition/data_points），不要拍平成几个标量后丢其余。
- **API 版本显式钉死**：每个工具对应的 SDK API 版本（v1/v2）写进 spec，不让实现自选。字段缺失先怀疑「打错版本」。
- **权威 schema = SDK 源码 model 类**：写 DTO 前必须 sparse-checkout `huaweicloud-sdk-java-v3` 对应 model 类，按真实 @JsonProperty + 字段类型映射。console API Explorer 抓不到（登录+SPA），仅作语义参考。禁止凭记忆猜字段/类名/类型。
- **类型贴齐 SDK**：时间用 SDK 的 OffsetDateTime（非 Long/String 臆测），SDK 枚举映射取 .getValue()。
- **契约测试兜底**：每个有响应 DTO 的工具必须有 *ContractTest——真实 SDK 样本（test/resources/sdk-samples/<svc>/）反序列化经 adapter 映射，断言覆盖全字段，漂移即 fail。

理由：DTO 提供稳定命名与防 SDK 泄漏的价值，但诊断 Agent 依赖完整信号；历史「最小子集」做法已造成系统性丢字段，本条予以纠正。

### 4.2 错误码统一映射

每个 adapter 在 catch SDK 异常时，必须映射到 `common.error.ErrorCode` 枚举，**不要让 SDK 异常透传到 MCP 层**。

错误码（首批）：

```
INVALID_PARAM           // 输入校验失败 (本地拦截)
UPSTREAM_THROTTLED      // 上游限流 (429)
UPSTREAM_AUTH_FAILED    // 上游鉴权失败 (401/403)
UPSTREAM_ERROR          // 上游 5xx
TIMEOUT                 // 调用超时
INTERNAL                // 序列化/未分类异常
```

每个错误同时携带：
- `retryable`: `true` / `false`
- `upstreamTraceId`: 华为云返回的 `X-Request-Id`（可为 null）

### 4.3 面向 Agent 的工具入参：禁止凭先验捏造上游查询结构（重要）

§4.1 约束响应侧无损；本条约束请求侧。核心：Agent 不得用自身先验捏造上游的
collector_name/collector_id/metric_set/function/查询 DSL 等。合法来源二选一：
- (a) 受控枚举 / 带 allowed-values 的 key（服务端目录翻译）；或
- (b) 先调只读「发现工具」拿到上游真实结构，再原样转发给查询工具
  （show_env_monitor_items → show_apm_monitor_item_view_config → 选一个 view → show_apm_trend）。

判定红线：被透传的结构若可能由模型凭记忆生成，违规；若必然来自前置发现工具的真实响应，合规。
凡是 env 局部/会变的标识（如 collector_id），一律运行时发现，禁止硬编码或跨 env 复用。
查询工具描述必须写明：入参取自哪个发现工具、调用顺序、禁止自行编造。

> 限流 / 重试 / 缓存策略另见 `docs/architecture.md`（Resilience4j 配置、RateLimiter 命名规则、
> Caffeine TTL 缓存策略）。

### 4.4 并发模型

- JDK 21 虚拟线程：`spring.threads.virtual.enabled=true`
- 华为云 SDK 是同步阻塞的 → 直接在虚拟线程里调用，不用单独的线程池
- 跨组件并发查询用 `CompletableFuture.allOf` + 虚拟线程 executor

### 4.5 配置注入

- AK/SK 通过 Vault 注入到环境变量 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK`
- 应用启动时一次性加载，**冷加载**，不支持热更新（轮换 = Pod 滚动重启）
- 启动时校验 AK/SK 非空，缺失则 fail-fast（健康检查不通过）

---

## 5. AI Coding 工作流

### 5.1 任务卡驱动

所有 AI 编码工作都从 `docs/tasks/T<NN>-<name>.md` 开始。任务卡包含：

- 任务范围与不在范围
- 输入（依赖的 spec、上游任务产物）
- 产物清单（要交付哪些文件）
- 验收标准
- AI 易错点提醒

**禁止脱离任务卡自由发挥**。AI 如果发现任务卡有歧义或不完整，应该停下来问，而不是猜。

### 5.2 增量式 PR

每个任务卡 = 一个 PR。PR 包含：

- 任务卡中列出的所有文件
- 通过所有任务卡列出的测试
- 通过 checkstyle / 通过 CI 流水线

### 5.3 测试先行（针对业务 tool）

对于实现 MCP tool 的任务（如 T05+）：

1. 先读 `docs/specs/tools/<tool_name>.md`
2. 先把 spec 中的"测试策略"段落转成测试代码骨架（测试方法名 + `@DisplayName`，方法体先 `fail("not implemented")`）
3. 再写实现，让测试转绿
4. 最后跑全套测试 + checkstyle

---

## 6. 通用约束清单（AI 生成时自查）

- [ ] 没有引入禁用清单中的依赖
- [ ] 没有把 SDK 类型泄漏到 adapter 之外
- [ ] 没有用 `null` 作为参数或返回值（除非明确允许）
- [ ] 没有用字符串拼接日志
- [ ] 没有打印敏感信息
- [ ] 所有 `if`/`for`/`while` 都带 `{}`
- [ ] 单个方法体 ≤ 50 行（不含空行与单行注释）；超长方法已拆分或已在 Javadoc 中标注「机械映射例外」
- [ ] 所有 public 方法都有 Javadoc（参数、返回值、异常）
- [ ] 所有类级和方法级 Javadoc 用**中文**编写（标识符 / `{@code}` / `{@link}` / HTML 标记保留英文）
- [ ] 行宽不超过 120
- [ ] import 分组符合规范
- [ ] 测试覆盖 spec 中列出的所有 UT/TC 用例
- [ ] 异常 catch 不是 `Exception` 而是明确类型
- [ ] 错误返回包含 `errorCode` 和 `retryable`

---

## 7. 关键路径速查

| 想做 | 看哪里 |
|---|---|
| 项目整体架构 | `docs/architecture.md` |
| 某个 tool 怎么实现 | `docs/specs/tools/<tool_name>.md` |
| 历史架构决策 | `docs/decisions/ADR-*.md` |
| AI 任务清单 | `docs/tasks/` |
| 错误码定义 | `com.huawei.smartom.agentic.common.error.ErrorCode` |
| 限流配置 | `application.yml` 的 `resilience4j.ratelimiter.instances` |
| 部署配置 | `helm/dpom-mcp-server/` |
| CI 流水线 | `.cloudbuild/build.yml` |
