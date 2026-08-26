/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import com.huawei.smartom.agentic.diagnosis.report.DiagnosisReportSourceSnapshot;

/**
 * 从服务本地持久化事实冻结 diagnosis-only 报告输入。
 *
 * @author Codex
 * @since 2026-08-26
 */
public interface DiagnosisReportSourceAuthority {
    /**
     * 读取精确调查及最新已发布诊断事件事实。
     *
     * @param investigationId 调查身份
     * @return 冻结报告源
     */
    DiagnosisReportSourceSnapshot freeze(String investigationId);
}
