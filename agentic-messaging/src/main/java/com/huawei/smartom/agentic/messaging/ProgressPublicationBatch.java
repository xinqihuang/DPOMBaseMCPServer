/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

/**
 * 一次持久化进度投影批次结果。
 *
 * @param published 已发布记录数
 * @param lastSequence 最后发布序号
 * @param resynchronize 是否因保留缺口需要从快照重建游标
 * @author Codex
 * @since 2026-08-25
 */
public record ProgressPublicationBatch(int published, long lastSequence, boolean resynchronize) {
}
