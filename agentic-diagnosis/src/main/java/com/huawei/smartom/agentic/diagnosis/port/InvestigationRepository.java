/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import java.util.Optional;

/**
  * Investigation 聚合持久化端口。
  * @author Codex
  * @since 2026-08-25
  */
public interface InvestigationRepository {
    /**
     * 按身份读取调查聚合。
     *
     * @param investigationId 调查身份
     * @return 调查聚合，可为空
     */
    Optional<Investigation> find(String investigationId);

    /**
     * 插入新调查聚合。
     *
     * @param investigation 调查聚合
     * @return 插入成功返回 true
     */
    boolean insert(Investigation investigation);

    /**
     * 使用乐观版本更新调查聚合。
     *
     * @param investigation 新聚合
     * @param expectedVersion 预期旧版本
     * @return 更新成功返回 true
     */
    boolean update(Investigation investigation, long expectedVersion);
}
