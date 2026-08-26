/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.CommandReceipt;
import com.huawei.smartom.agentic.diagnosis.model.ExternalCallRecord;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationCheckpoint;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationRun;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStep;
import com.huawei.smartom.agentic.diagnosis.model.Observation;

import org.apache.ibatis.annotations.Param;

/**
 * Investigation 明细事实的类型化 MyBatis 映射器。
 *
 * @author Codex
 * @since 2026-08-25
 */
public interface InvestigationDetailMapper {

    /**
     * 插入运行事实。
     * @param value 运行
     * @return 影响行数
     */
    int insertRun(InvestigationRun value);

    /**
     * 插入步骤事实。
     * @param investigationId 调查身份
     * @param value 步骤
     * @return 影响行数
     */
    int insertStep(@Param("investigationId") String investigationId,
                   @Param("value") InvestigationStep value);

    /**
     * 插入观察事实。
     * @param value 观察
     * @return 影响行数
     */
    int insertObservation(Observation value);

    /**
     * 插入假设事实。
     * @param value 假设行
     * @return 影响行数
     */
    int insertHypothesis(HypothesisRow value);

    /**
     * 插入 checkpoint。
     * @param value checkpoint
     * @return 影响行数
     */
    int insertCheckpoint(InvestigationCheckpoint value);

    /**
     * 插入幂等命令收据。
     * @param value 命令收据
     * @return 影响行数
     */
    int insertCommandReceipt(CommandReceipt value);

    /**
     * 插入外部调用记录。
     * @param value 外部调用记录
     * @return 影响行数
     */
    int insertExternalCall(ExternalCallRecord value);
}
