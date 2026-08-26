/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

/**
  * 聚合终态、进度、审计与 publication intent 的原子提交端口。
  * @author Codex
  * @since 2026-08-25
  */
public interface InvestigationTransactionPort {
    /**
     * 在一个本地事务中提交所有终态事实。
     *
     * @param commit 终态提交内容
     * @return 乐观更新成功返回 true
     */
    boolean commitTerminal(TerminalCommit commit);
}
