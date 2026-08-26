/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

/**
  * 不透明领域身份生成端口。
  * @author Codex
  * @since 2026-08-25
  */
public interface IdPort {
    /**
     * 在指定命名空间生成不透明身份。
     *
     * @param namespace 身份命名空间
     * @return 新身份
     */
    String next(String namespace);
}
