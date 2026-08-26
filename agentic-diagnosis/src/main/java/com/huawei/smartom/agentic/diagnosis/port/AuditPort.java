/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import com.huawei.smartom.agentic.diagnosis.model.AuditRecord;

/**
  * 追加式调查审计端口。
  * @author Codex
  * @since 2026-08-25
  */
public interface AuditPort {
    /**
     * 追加不可变审计记录。
     *
     * @param record 审计记录
     */
    void append(AuditRecord record);
}
