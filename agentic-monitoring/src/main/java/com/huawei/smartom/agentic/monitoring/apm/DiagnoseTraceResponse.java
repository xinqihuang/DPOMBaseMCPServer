/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmGetTopologyResponse;

import java.util.List;

/**
 * {@code diagnose_trace} 的聚合诊断包：从一个 trace_id 一步得到根因线索。
 *
 * <p>包含调用图拓扑、事件计数概览、挑出的可疑事件（含详情与 clob 全文），
 * 以及各阶段的非致命错误。任一阶段失败只在 {@code errors} 记录、对应字段置空，
 * 不影响其余阶段——与 {@code correlate_incident} 的分支独立语义一致。
 *
 * @param traceId         被诊断的 trace id
 * @param topology        调用图拓扑；拓扑阶段失败时为 {@code null}
 * @param totalEventCount 调用链事件总数；事件阶段失败时为 {@code null}
 * @param errorEventCount 其中标记出错的事件数；事件阶段失败时为 {@code null}
 * @param suspects        挑出的可疑事件列表（出错优先，其次最慢），不会为 {@code null}
 * @param errors          各阶段非致命错误列表，不会为 {@code null}
 * @author huangxinqi
 * @since 2026-06-18
 */
public record DiagnoseTraceResponse(
        String traceId,
        ApmGetTopologyResponse topology,
        Integer totalEventCount,
        Integer errorEventCount,
        List<SuspectEvent> suspects,
        List<DiagnoseStageError> errors) {
}
