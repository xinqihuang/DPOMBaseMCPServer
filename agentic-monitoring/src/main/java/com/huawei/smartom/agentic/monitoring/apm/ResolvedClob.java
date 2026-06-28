/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.apm;

/**
 * 一条已解析的 clob（超长字段）文本。
 *
 * <p>event 详情中的超长值（完整异常堆栈 / 完整 SQL）以 clob 引用形式出现在 {@code tags} 里，
 * key 形如 {@code *_clob_id}，value 为 clob id。本记录承载下钻解析后的全文。
 *
 * @param tagKey 触发解析的 tag key（如 {@code stacktrace_clob_id} / {@code sql_clob_id}）
 * @param clobId clob 引用 id
 * @param text   解析出的全文；当该 clob 下钻失败时为 {@code null}（失败原因见响应的 {@code errors}）
 * @author huangxinqi
 * @since 2026-06-18
 */
public record ResolvedClob(
        String tagKey,
        String clobId,
        String text) {
}
