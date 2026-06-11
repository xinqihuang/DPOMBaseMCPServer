/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.error.ErrorResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * MCP tool 层统一的调用包装器：执行工具主体逻辑，把所有失败转换为结构化
 * {@link ErrorResponse} 返回给 MCP 客户端（大模型 Agent）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>错误<strong>不向 MCP 框架抛出</strong>——Spring AI MCP Server 对工具异常只回传
 *       {@code e.getMessage()} 一行文本，Agent 拿不到 error_code / retryable / hint，
 *       无法决策下一步动作。</li>
 *   <li>本类 catch {@code RuntimeException} 作为进程边界兜底，是 CLAUDE.md §3.4
 *       「明确捕获类型」的有意例外：任何未分类异常都映射为 {@code INTERNAL}，
 *       完整堆栈只进服务端日志，返回给 Agent 的消息仅含异常类名与 message。</li>
 *   <li>成功与失败路径都记录工具名与耗时。</li>
 * </ul>
 *
 * <p>包私有：仅供 {@code @Component} Tool 类使用，不外暴露。
 *
 * @author h00884391
 * @since 2026-06-10
 */
final class ToolCallSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ToolCallSupport.class);

    private ToolCallSupport() {
        // 工具类，禁止实例化
    }

    /**
     * 执行工具主体逻辑并统一处理异常。
     *
     * @param toolName MCP 工具名（与 {@code @Tool(name = ...)} 一致），用于日志定位
     * @param action   工具主体逻辑（入参解析 + service 调用），不可为 {@code null}
     * @return 成功时返回 {@code action} 的结果；失败时返回 {@link ErrorResponse}
     */
    static Object execute(String toolName, Supplier<?> action) {
        long start = System.currentTimeMillis();
        try {
            Object result = action.get();
            LOG.info("{} succeeded, durationMs={}", toolName, System.currentTimeMillis() - start);
            return result;
        }
        catch (SmartomException e) {
            LOG.warn("{} failed, errorCode={}, upstreamTraceId={}, durationMs={}",
                    toolName, e.getErrorCode(), e.getUpstreamTraceId(),
                    System.currentTimeMillis() - start);
            return ErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getUpstreamTraceId());
        }
        catch (RuntimeException e) {
            LOG.error("{} failed unexpectedly, durationMs={}",
                    toolName, System.currentTimeMillis() - start, e);
            return ErrorResponse.of(ErrorCode.INTERNAL, internalMessage(e), null);
        }
    }

    /**
     * 构造返回给 Agent 的 {@code INTERNAL} 错误消息：仅含异常类名与 message，不含堆栈。
     *
     * @param cause 未分类的运行时异常
     * @return 简短的错误描述
     */
    private static String internalMessage(RuntimeException cause) {
        String detail = cause.getMessage() == null ? "" : ": " + cause.getMessage();
        return "Unexpected internal error (" + cause.getClass().getSimpleName() + ")" + detail;
    }
}
