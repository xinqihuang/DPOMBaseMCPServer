/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.common.error;

/**
 * 返回给 MCP 客户端的统一错误码。
 *
 * <p>每个错误码都携带 {@code retryable} 标记，指示 MCP 客户端（Agent）能否安全地重试请求；
 * 同时携带面向 Agent 的 {@code hint} 行动建议（英文，与 MCP 工具描述语言保持一致），
 * 告诉 Agent 拿到该错误后的最优下一步动作。提示文案在此单点维护，工具层不得另写。
 *
 * @author h00884391
 * @since 2026-05-21
 */
public enum ErrorCode {

    /** Input validation failed locally; never retryable. */
    INVALID_PARAM(false,
            "Do not retry with the same arguments. Re-read the tool description, "
                    + "fix the invalid parameter(s), then call again."),

    /** Upstream rate-limited (e.g., HTTP 429). Safe to retry after backoff. */
    UPSTREAM_THROTTLED(true,
            "Huawei Cloud rate limit hit. Wait a few seconds and retry; "
                    + "if it persists, reduce call frequency or narrow the query."),

    /** Upstream authentication / authorization failed (HTTP 401 / 403). */
    UPSTREAM_AUTH_FAILED(false,
            "Server-side credential or permission problem. Changing arguments will not help; "
                    + "stop retrying and report this together with upstream_trace_id."),

    /** Upstream rejected request parameters (HTTP 4xx other than auth / throttling). */
    UPSTREAM_INVALID_PARAM(false,
            "Huawei Cloud rejected one or more request parameters. Do not retry the same call. "
                    + "Use the required discovery tool and correct the arguments; report "
                    + "upstream_trace_id if the discovered arguments are still rejected."),

    /** Upstream server error (HTTP 5xx). */
    UPSTREAM_ERROR(true,
            "Huawei Cloud returned a server error. Retry once after a short backoff; "
                    + "if it persists, report upstream_trace_id to the operator."),

    /** Call timeout. */
    TIMEOUT(true,
            "The upstream call timed out. Retry; if timeouts persist, "
                    + "narrow the time range or reduce the page size."),

    /** Serialization or otherwise unclassified internal error. */
    INTERNAL(false,
            "Unexpected server-side error, not caused by your arguments. Do not retry the "
                    + "same call; try an alternative tool or report error_message to the operator."),

    /** OBS evidence transfer is not enabled or no real adapter is available. */
    OBS_UNAVAILABLE(false,
            "OBS evidence transfer is not enabled or the adapter is unavailable. Do not retry; "
                    + "report to the operator.");

    private final boolean retryable;
    private final String hint;

    ErrorCode(boolean retryable, String hint) {
        this.retryable = retryable;
        this.hint = hint;
    }

    /**
     * 返回以本错误码失败的操作是否可以安全重试。
     *
     * @return {@code true} 表示 MCP 客户端（Agent）可以在退避后重试该请求；
     *         {@code false} 表示终止性错误，例如 {@code INVALID_PARAM} 或 {@code UPSTREAM_AUTH_FAILED}
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * 返回面向 Agent 的行动建议，指导其收到该错误后的下一步动作
     * （修正参数 / 退避重试 / 停止并上报等）。
     *
     * @return 英文提示文案，不会为 {@code null} 或空串
     */
    public String getHint() {
        return hint;
    }
}
