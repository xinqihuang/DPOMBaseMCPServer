# T34 — 加固 APM Trace 诊断链

## 目标

修复 `diagnose_trace` 并发下钻触发自身限流的问题，让最多 20 个可疑事件的真实只读诊断能够稳定完成。

## 验收

- `maxSuspectEvents` 仅接受 1–20，默认 5。
- 事件详情与 clob 下钻有界、顺序执行。
- `mvn clean verify` 通过。
- 真实 20-event 诊断不再出现本地 `apm-readonly` 限流错误。
