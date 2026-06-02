/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.common.validation;

import com.huawei.smartom.agentic.common.exception.InvalidParamException;

/**
 * 输入校验通用工具。集中托管 {@code isBlank} 等小型断言，并提供与
 * {@link InvalidParamException} 直接对接的 {@code requireXxx} 方法，
 * 避免业务层重复书写 null/空串判断后抛异常的样板代码。
 *
 * @author h00884391
 * @since 2026-06-02
 */
public final class Validations {

    private Validations() {
        // 工具类，禁止实例化
    }

    /**
     * 与 {@link String#isBlank()} 等价，但允许 {@code null}。
     *
     * @param value 待检查字符串
     * @return 当 {@code value} 为 {@code null}、空串或全空白字符时返回 {@code true}
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 断言字符串非空，否则抛出 {@link InvalidParamException}。
     *
     * @param value     待检查字符串
     * @param fieldName 字段名（用于错误信息），如 {@code "namespace"}
     * @return 原值（便于链式赋值）
     * @throws InvalidParamException 当 {@code value} 为空时抛出
     */
    public static String requireNonBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new InvalidParamException(fieldName + " is required");
        }
        return value;
    }

    /**
     * 断言对象非 {@code null}，否则抛出 {@link InvalidParamException}。
     *
     * @param value     待检查对象
     * @param fieldName 字段名
     * @param <T>       任意对象类型
     * @return 原值（便于链式赋值）
     * @throws InvalidParamException 当 {@code value} 为 {@code null} 时抛出
     */
    public static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new InvalidParamException(fieldName + " is required");
        }
        return value;
    }
}
