/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.model;

/**
  * 领域值对象的无框架校验规则。
  * @author Codex
  * @since 2026-08-25
  */
final class DomainRules {
    private DomainRules() {}

    static String id(String value, String field) {
        if (value == null
                || value.isBlank()
                || value.length() > 128
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            throw new IllegalArgumentException(field);
        }
        return value;
    }

    static String code(String value, String field) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException(field);
        }
        return value;
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field);
        }
        return value;
    }
}
