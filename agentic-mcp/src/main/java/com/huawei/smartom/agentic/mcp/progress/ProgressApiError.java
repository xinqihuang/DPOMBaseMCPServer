/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

/**
 * 不携带异常内容的稳定错误响应。
 *
 * @param errorCode 稳定错误编码
 * @param resynchronize 是否必须重新获取快照
 * @author Codex
 * @since 2026-08-25
 */
public record ProgressApiError(String errorCode, boolean resynchronize) {
}
