# ADR-003: 不使用 Lombok

- 状态: Accepted
- 日期: 2026-05-21

## Context

DPOMBaseMCPServer 是 AI Coding 主导的项目。AI 在生成代码时使用 Lombok 经常出现以下问题：

- `@Builder` / `@Value` / `@Data` 混用，导致行为不一致
- 与 Jackson / Spring 反射的边界情况（如 `@NoArgsConstructor` 缺失）反复踩坑
- 调试时 IDE 跳转看不到生成的方法，新人迷惑
- 与 JDK 21 `record` 功能重叠

## Decision

**禁用 Lombok**。替代方案：

| 场景 | 替代 |
|---|---|
| 不可变值对象 (DTO) | JDK 21 `record` |
| 需要可变字段的 POJO（如 Spring Boot Config Properties） | 普通类 + getter/setter（AI 写完整 setter 是稳的） |
| 链式 builder | 手写 builder（一次性写完，后续不改） |
| 日志变量 `log` | `private static final Logger log = LoggerFactory.getLogger(X.class);` 一行手写 |

## Consequences

- ✅ 代码所见即所得，调试简单
- ✅ AI 生成稳定（不会因 Lombok 注解组合错乱）
- ✅ JDK 21 record 已能覆盖 80% DTO 场景
- ⚠️ 普通 POJO 略冗长，但对 AI 生成无影响（甚至更稳）

## Alternatives Considered

- **保留 Lombok 但限制注解集合**（只允许 `@Getter @Setter @ToString`）：增加规则复杂度，不值得
