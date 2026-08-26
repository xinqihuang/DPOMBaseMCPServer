# CES Tool 验证报告（2026-06-29）

> 凭证来源：`.env.local`（AK=`<REDACTED_AK>`，region=`cn-north-9`，账号域 `MOS`）
> 验证对象：`agentic-mcp` 0.0.1-SNAPSHOT，Spring AI MCP Server 1.0.4
> 结论：**发现并修复一个 P0 bug**——`list_ces_metrics` / `query_ces_metric_data` 传 `namespace="SYS.ECS"` 必 400。修复后全部 CES tool 真实回归通过。

---

## 1. 验证流程总览

| 步骤 | 动作 | 结果 |
|---|---|---|
| 1 | AK/SK 配对自检（IAM 签名） | ✅ HTTP 200，凭证有效 |
| 2 | 本地构建 `mvn -pl agentic-mcp -am package -DskipTests` | ✅ 产出 53MB 可执行 jar |
| 3 | 启动 MCP Server（local profile，region=cn-north-9） | ✅ readiness UP，8080/8081 |
| 4 | 走完整 MCP SSE 握手调真实 CES API | ❌ 发现 bug（见 §3） |
| 5 | 根因定位（反编译 Spring AI） | ✅ 确认 `JsonParser.toTypedObject` 对 enum 走 `Enum.valueOf` |
| 6 | 修复 + 单测 + checkstyle + 真实回归 | ✅ 全绿（见 §5、§6） |

---

## 2. AK/SK 校验

### 2.1 第一对凭证（已作废）

`.env.local` 原始内容：

```
HUAWEICLOUD_SDK_AK=<REDACTED_AK>
HUAWEICLOUD_SDK_SK=<REDACTED_SK>
HUAWEICLOUD_SDK_REGION=cn-north-4
```

两路独立验证一致返回 401：

```
HTTP 401  APIGW.0301
Incorrect IAM authentication information: verify ak sk signature failed
```

- `scripts/smoke/check-aksk.py`（手写 SDK-HMAC-SHA256 签名）
- 华为云官方 Python SDK `huaweicloudsdkiam`

结论：AK/SK 不匹配（SK 已轮换或抄写损坏）。用户更新凭证后继续。

### 2.2 第二对凭证（本次使用）

```
HUAWEICLOUD_SDK_AK=<REDACTED_AK>
HUAWEICLOUD_SDK_SK=<REDACTED_SK>
HUAWEICLOUD_SDK_REGION=cn-north-9
HUAWEICLOUD_PROJECT_ID=81679072734a4608ae65cd3940458c51
```

`check-aksk.py` 输出：

```
HTTP 200  -> AK/SK 配对正确，凭证有效 ✅
{"projects":[{"domain_id":"fb05f078b7be4d47970834e82f078652","name":"MOS",...}]}
```

> **变量名映射**：应用期望 `HUAWEICLOUD_AK` / `HUAWEICLOUD_SK` / `HUAWEICLOUD_REGION`，`.env.local` 用 `HUAWEICLOUD_SDK_AK` / `HUAWEICLOUD_SDK_SK` / `HUAWEICLOUD_SDK_REGION`，启动时需映射。`huaweicloud.region` 在 `application.yml` 硬编码 `cn-southwest-2`，通过环境变量 `HUAWEICLOUD_REGION` 覆盖为 `cn-north-9`（Spring Boot relaxed binding）。

---

## 3. 发现的 Bug：CES 枚举入参 tool 400

### 3.1 现象

通过完整 MCP SSE 握手（`GET /sse` 拿 sessionId → `initialize` → `notifications/initialized` → `tools/call`）调用 `list_ces_metrics`：

```jsonc
// 请求
{"name":"list_ces_metrics","arguments":{"namespace":"SYS.ECS","limit":5}}

// 响应
{"jsonrpc":"2.0","id":10,"result":{"content":[{"type":"text",
  "text":"No enum constant com.huawei.smartom.agentic.adapter.ces.dto.CesNamespace.SYS.ECS"}],
  "isError":true}}
```

`namespace="SYS.ECS"` 正是 `@ToolParam` 描述、`@JsonValue`、`scripts/smoke/smoke-list_ces_metrics.sh` 三处一致要求的写法，却返回 `isError=true`。

### 3.2 影响面（实测确认）

| Tool | `namespace` 参数类型 | `SYS.ECS` | 说明 |
|---|---|---|---|
| `list_ces_metrics` | `CesNamespace`（直接 enum） | ❌ 400 | `Enum.valueOf` 路径 |
| `query_ces_metric_data` | `CesNamespace`（直接 enum） | ❌ 400 | 同上 |
| `batch_query_ces_metric_data` | `List<CesBatchMetricQueryInput>` | ✅ | `metrics` 是 ParameterizedType，走 Jackson `fromJson`，`@JsonCreator` 生效 |
| `list_alarms` | `String` | ✅ | 非 enum，不受影响 |

### 3.3 根因（反编译 `spring-ai-model` 1.0.4 确认）

`org.springframework.ai.util.json.JsonParser.toTypedObject` 字节码（offset 165–180）：

```
165: invokevirtual Class.isEnum()
169: ifeq 181
172: aload_2
173: aload_0
174: invokevirtual Object.toString()
177: invokestatic Enum.valueOf(Class, String)   ← 直接 Enum.valueOf，绕过 Jackson
180: areturn
```

`MethodToolCallback.buildTypedArgument` 在 `Type instanceof Class` 时调 `toTypedObject`。当目标参数 `isEnum()` 为 true，**直接 `Enum.valueOf(cls, value.toString())`，完全不走 Jackson**，因此 `CesNamespace` 上的 `@JsonCreator fromValue`（兼容 `SYS.ECS` / `SYS_ECS`）形同虚设。`Enum.valueOf` 要的是枚举常量名 `SYS_ECS`，而 tool 描述让 Agent 传字面量 `SYS.ECS`，必然抛 `No enum constant`。

### 3.4 旁证：枚举与 Jackson 本身没问题

用裸 `ObjectMapper`（2.17.2，与 Spring Boot 3.4 一致）反序列化 `CesNamespace`：

```
"SYS.ECS" -> SYS_ECS value=SYS.ECS   ✅
"SYS_ECS" -> SYS_ECS value=SYS.ECS   ✅
```

`@JsonCreator fromValue` 在 Jackson 路径下完全正常。问题只在 Spring AI 参数绑定这一层。

`CesNamespace.fromValue` 源码（本就兼容两种写法）：

```java
@JsonCreator
public static CesNamespace fromValue(String value) {
    if (value == null) throw new IllegalArgumentException("namespace must not be null");
    for (CesNamespace ns : values()) {
        if (ns.value.equals(value) || ns.name().equals(value)) return ns;  // SYS.ECS 或 SYS_ECS
    }
    throw new IllegalArgumentException("unsupported CES namespace: " + value + ...);
}
```

---

## 4. 修复方案

把受影响工具的 `namespace` 参数从 `CesNamespace` 改为 `String`，在 Tool 层用 `CesNamespace.fromValue` 做服务端目录翻译（CLAUDE.md §4.3 (a)）。

### 4.1 `ToolValidations` 新增 `resolveCesNamespace`

```java
static String resolveCesNamespace(String namespace) {
    if (namespace == null) return null;                 // 必填校验交 service 层
    try {
        return CesNamespace.fromValue(namespace).getValue();
    } catch (IllegalArgumentException e) {
        throw new InvalidParamException(e.getMessage()); // → INVALID_PARAM
    }
}
```

保留既有 `cesNamespaceValue(CesNamespace)` 供 `CesBatchMetricDataTool` 继续使用。

### 4.2 两个 Tool 签名改动

```java
// 改前
CesNamespace namespace, ...
ToolValidations.cesNamespaceValue(namespace)

// 改后
String namespace, ...
ToolValidations.resolveCesNamespace(namespace)
```

`@ToolParam` 描述由 `"closed enum"` 改为 `"closed set of 15 values"`，仍逐项列出 15 个合法值（Schema 不再自动生成 enum，描述文本是 Agent 唯一的封闭集提示）。

### 4.3 关键细节：`req` 构造必须挪进 lambda

`CesMetricsTool` 原代码 `req` 构造在 `ToolCallSupport.execute` 的 lambda **外面**：

```java
// 错误写法（resolveCesNamespace 抛出的 InvalidParamException 逃出 try/catch）
CesListMetricsRequest req = new CesListMetricsRequest(
        ToolValidations.resolveCesNamespace(namespace), ...);
return ToolCallSupport.execute("list_ces_metrics", () -> service.listMetrics(req));

// 正确写法
return ToolCallSupport.execute("list_ces_metrics", () -> {
    CesListMetricsRequest req = new CesListMetricsRequest(
            ToolValidations.resolveCesNamespace(namespace), ...);
    return service.listMetrics(req);
});
```

原代码 `cesNamespaceValue` 不抛异常所以无此问题；改用 `resolveCesNamespace` 后必须挪进 lambda，否则校验异常不会被 `ToolCallSupport` 统一 catch 成 `ErrorResponse`，而是裸抛 500。单测 `unknownNamespaceRejected` 第一轮正是挂在这里，修复后通过。

### 4.4 不改的部分

- `batch_query_ces_metric_data` / `CesBatchMetricQueryInput`：枚举在 record 字段内，走 Jackson 路径，正常，保留 Schema 封闭集
- `CesNamespace` 枚举本身：`@JsonCreator fromValue` 设计正确
- adapter / monitoring / 响应 DTO：namespace 在 adapter 边界本就是 String

---

## 5. 测试与静态检查

```
mvn -pl agentic-mcp -am test          → 105 tests, 0 failures, 0 errors  ✅
mvn -pl agentic-mcp -am checkstyle:check → BUILD SUCCESS                ✅
```

新增/改动的回归用例：

| 测试 | 覆盖点 |
|---|---|
| `CesMetricsToolTest.successPassthrough` | 传 `"SYS.ECS"`，断言 `req.namespace()=="SYS.ECS"`（证明 fromValue 生效） |
| `CesMetricsToolTest.unknownNamespaceRejected` | `"SYS.NOPE"` → `INVALID_PARAM`，不调 service |
| `CesMetricDataToolTest.unknownNamespaceRejected` | 同上 |
| `CesNamespaceSchemaTest` | 去掉 list/query 的 Schema 枚举断言（参数已非枚举），保留 batch |

---

## 6. 真实回归（cn-north-9 凭证，修复后）

| 调用 | 入参 | 结果 |
|---|---|---|
| `list_ces_metrics` | `namespace="SYS.ECS", limit=3` | ✅ `isError=false`，返回真实指标（`network_vm_pps_out` 等，含真实 instance_id） |
| `list_ces_metrics` | `namespace="SYS_ECS", limit=3` | ✅ 两种写法都接受 |
| `list_ces_metrics` | `namespace="SYS.NOPE", limit=3` | ✅ 结构化 `INVALID_PARAM`：`unsupported CES namespace: SYS.NOPE, expected one of [SYS_ECS, ...]` |
| `query_ces_metric_data` | `namespace="SYS.ECS", metricName=disk_write_requests_rate, ...` | ✅ `isError=false`，返回 `{"metricName":"disk_write_requests_rate","datapoints":[]}`（窗口内无数据，但调用成功） |
| `batch_query_ces_metric_data` | `metrics=[{namespace:"SYS.ECS",...}]` | ✅ 返回真实数据点（修复前就已正常） |
| `list_alarms` | `namespace="SYS.ECS", limit=5` | ✅ 返回真实 CES 告警（`namespace` 是 String，从未受影响） |

---

## 7. 产物

- 任务卡：`docs/tasks/T31-ces-namespace-string-param.md`
- 提交：`0aa095d` — `T31: CES namespace 工具入参由枚举改为 String`（7 文件 +152/−43，未推送）
- 项目记忆：`spring-ai-enum-param-valueof.md`（记录此 Spring AI 框架坑，后续任何 enum 入参新 tool 都会踩）

---

## 9. 本机部署 + 对接 OpenClaw（2026-06-29 续）

将修复后的 MCP Server 部署为本机常驻服务，并对接 OpenClaw（`/Users/huangxinqi/.local/bin/openclaw`，v2026.6.10），跑通端到端 agent turn。

### 9.1 部署：launchd 常驻服务

- **plist**：`~/Library/LaunchAgents/com.huawei.smartom.dpom-mcp.plist`（chmod 600）
- **进程**：`/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java -jar <repo>/agentic-mcp/target/agentic-mcp-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`
- **环境变量**：`HUAWEICLOUD_AK` / `HUAWEICLOUD_SK` / `HUAWEICLOUD_REGION=cn-north-9` / `HUAWEICLOUD_PROJECT_ID`（从 `.env.local` 映射；`huaweicloud.region` 在 yml 硬编码 `cn-southwest-2`，靠 `HUAWEICLOUD_REGION` 覆盖）
- **保活**：`RunAtLoad` + `KeepAlive` + `ThrottleInterval=10`，日志 `<repo>/logs/dpom-mcp.{out,err}.log`
- **端口**：8080(SSE) / 8081(actuator)，readiness `UP`
- **管理**：`launchctl unload/load ~/Library/LaunchAgents/com.huawei.smartom.dpom-mcp.plist`

### 9.2 对接 OpenClaw

```
openclaw mcp add dpom-mcp --transport sse --url http://127.0.0.1:8080/sse --no-probe
openclaw mcp probe dpom-mcp   →  28 tools, resources, prompts ✅
openclaw mcp reload
```

**坑 1：SSRF 拦截 localhost**。OpenClaw 的 mcp-http SSRF 守卫对 `localhost` 解析成 `127.0.0.1` 后与 `allowedOrigins` 里的 `http://localhost:8080` 不同源，私网检查不跳过，报 `Blocked: resolves to private/internal/special-use IP address`。改用 `http://127.0.0.1:8080/sse` 注册即通过（origin 精确匹配，`resolveSsrFPolicyForUrl` 自动把 hostname 加进 allowedHostnames 跳过私网检查）。`browser.ssrfPolicy.*` 对 mcp-http 无效。

**坑 2：模型 provider**。OpenClaw `mcp.servers` 只给内嵌 agent 运行时用；`claude-cli` 后端是 passthrough 到 Claude Code，不接 OpenClaw 的 MCP。本机能跑的模型经 modelarts 的 anthropic 兼容代理（env `ANTHROPIC_BASE_URL=https://api.modelarts-maas.com/anthropic` + `ANTHROPIC_AUTH_TOKEN`，实际模型 `glm-5.2`）。在 `~/.openclaw/openclaw.json` 注册自定义 provider：

```jsonc
{
  "models": {
    "providers": {
      "modelarts": {
        "baseUrl": "https://api.modelarts-maas.com/anthropic",
        "api": "anthropic-messages",
        "apiKey": "<ANTHROPIC_AUTH_TOKEN>",
        "models": [{ "id": "glm-5.2", "name": "GLM 5.2 (modelarts)", "api": "anthropic-messages", "input": ["text"] }]
      }
    }
  }
}
```

### 9.3 端到端 agent turn

```
openclaw agent --local --agent main --model modelarts/glm-5.2 \
  --session-key main:ces-verify2 \
  --message "调用 list_ces_metrics，namespace=SYS.ECS, limit=5，列出 metric_name"
```

Agent 实际调用 `dpom-mcp__list_ces_metrics`（`namespace=SYS.ECS, limit=5`），返回真实指标：

| metric_name | 单位 |
|---|---|
| network_vm_pps_out | Packet/s |
| network_vm_pps_in | Packet/s |
| network_vm_newconnections | connect/s |
| network_vm_connections | Count |
| network_vm_bandwidth_out | Byte/s |

`total=13239, has_more=true`。**Agent 传 `SYS.ECS` 点分形式且成功**——T31 修复在生效。

完整链路：`OpenClaw agent → OpenClaw MCP 运行时 → SSE → DPOMBaseMCPServer(launchd) → 华为云 CES(cn-north-9) → 真实数据` ✅

### 9.4 凭证与安全提示

- launchd plist 与 `~/.openclaw/openclaw.json` 均含明文 AK/SK 与 modelarts token（均 chmod 600 / 本机 dev 可接受）。**切勿把 `~/.openclaw/openclaw.json` 提交进 git**。
- 项目记忆 `local-deploy-openclaw.md` 记录了部署方式与两个坑，换机/换凭证时参考。

## 10. 遗留与建议

1. **smoke 脚本需更新**：`scripts/smoke/smoke-list_ces_metrics.sh` 直接 POST `/mcp/messages` 不带 sessionId，Spring AI SSE 传输要求先 `GET /sse` 握流，脚本当前会 400（`Session ID missing`）。建议改成先建立 SSE 会话再 POST，或单独写一个 Python 客户端（本次验证用的 `/tmp/mcp_call.py` 可作蓝本）。
2. **`docs/specs/tools/list_ces_metrics.md` / `query_ces_metric_data.md` 的 namespace 描述**仍写「string + 正则 `^[A-Z]...`」，与 T24 之后的「封闭 15 值」事实有漂移。本次未改 spec（避免扩大改动面），建议后续小卡对齐。
3. **`docs/tasks/README.md` 任务索引**未登记 T24–T31，索引维护滞后，建议择期补齐。
