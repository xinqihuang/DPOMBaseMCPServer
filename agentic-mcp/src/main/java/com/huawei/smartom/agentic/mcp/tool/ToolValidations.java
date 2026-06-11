/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.tool;

import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricFilter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricPeriod;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNamespace;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

/**
 * Tool 层共用的小型校验工具。负责把 MCP 入参的原始字符串 / 数字解析为
 * 项目内的枚举类型，并把底层枚举抛出的 {@link IllegalArgumentException} 统一映射成
 * {@link InvalidParamException}，进而被 Tool 的统一 catch 转成 {@code INVALID_PARAM} 错误响应。
 *
 * <p>包私有：仅供 {@code @Component} Tool 类使用，不外暴露。
 *
 * @author h00884391
 * @since 2026-06-02
 */
final class ToolValidations {

    private ToolValidations() {
        // 工具类，禁止实例化
    }

    /**
     * 将 MCP 入参字符串解析为 {@link CesMetricFilter}。
     *
     * @param value MCP 入参字面量，如 {@code "average"}
     * @return 对应枚举值
     * @throws InvalidParamException {@code value} 为 {@code null} 或不在枚举集合内时抛出
     */
    static CesMetricFilter requireCesFilter(String value) {
        if (value == null) {
            throw new InvalidParamException("filter is required");
        }
        try {
            return CesMetricFilter.fromValue(value);
        }
        catch (IllegalArgumentException e) {
            throw new InvalidParamException(e.getMessage());
        }
    }

    /**
     * 将受控枚举 namespace 转为 CES API 字面量（T24，.getValue() 映射集中于 Tool 层此一处）。
     *
     * <p>{@code null} 透传为 {@code null}——必填校验由 service 层完成，
     * 以保证缺失时返回 {@code INVALID_PARAM} 而非 NPE。
     *
     * @param namespace 受控枚举命名空间，可为 {@code null}
     * @return CES API 字面量（如 {@code SYS.ECS}），入参为 {@code null} 时返回 {@code null}
     */
    static String cesNamespaceValue(CesNamespace namespace) {
        return namespace == null ? null : namespace.getValue();
    }

    /**
     * 将 MCP 入参秒数解析为 {@link CesMetricPeriod}。
     *
     * @param value MCP 入参秒数，如 {@code 300}
     * @return 对应枚举值
     * @throws InvalidParamException {@code value} 为 {@code null} 或不在枚举集合内时抛出
     */
    static CesMetricPeriod requireCesPeriod(Integer value) {
        if (value == null) {
            throw new InvalidParamException("period is required");
        }
        try {
            return CesMetricPeriod.fromSeconds(value);
        }
        catch (IllegalArgumentException e) {
            throw new InvalidParamException(e.getMessage());
        }
    }
}
