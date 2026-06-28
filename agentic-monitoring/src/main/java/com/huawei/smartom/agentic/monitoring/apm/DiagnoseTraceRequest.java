/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

/**
 * {@code diagnose_trace} 编排工具的入参。
 *
 * <p>只需一个 {@code traceId} 即可起步：服务内部会自动拉取拓扑与调用链事件、
 * 挑出出错 / 最慢的可疑 span，再下钻 event 详情与 clob 文本。
 *
 * @param traceId          APM trace id，必填，来自告警载荷或 {@code query_traces} 结果
 * @param businessId       APM 业务 id，可选；为 {@code null} 时回落到服务端配置默认值（仅用于 clob 下钻）
 * @param maxSuspectEvents 最多深挖的可疑 event 数，可选；为 {@code null} 时用默认值
 * @param includeSlowest   是否在无出错 event 时也纳入最慢的若干 span，可选；为 {@code null} 时默认纳入
 * @author huangxinqi
 * @since 2026-06-18
 */
public record DiagnoseTraceRequest(
        String traceId,
        Long businessId,
        Integer maxSuspectEvents,
        Boolean includeSlowest) {
}
