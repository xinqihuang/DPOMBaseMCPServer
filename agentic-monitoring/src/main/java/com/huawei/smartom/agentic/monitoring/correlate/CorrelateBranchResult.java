/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.correlate;

/**
 * {@code correlate_incident} 单个分支的执行结果。
 *
 * <p>三态：
 * <ul>
 *   <li>{@code skipped=true}：本分支输入缺失，未调用上游</li>
 *   <li>{@code data != null}：分支成功，{@code data} 为该分支的响应 DTO</li>
 *   <li>{@code error != null}：分支失败，{@code error} 给出错误码与简要消息</li>
 * </ul>
 *
 * @param skipped 是否跳过该分支
 * @param data    成功时的响应负载（可能为 {@code null}）
 * @param error   失败时的错误描述（可能为 {@code null}）
 * @author h00884391
 * @since 2026-05-28
 */
public record CorrelateBranchResult(
        boolean skipped,
        Object data,
        CorrelateError error) {

    /**
     * 构造一个 skipped 状态的分支结果。
     *
     * @return skipped 状态的实例
     */
    public static CorrelateBranchResult ofSkipped() {
        return new CorrelateBranchResult(true, null, null);
    }

    /**
     * 构造一个成功分支结果。
     *
     * @param data 成功负载，不能为 {@code null}
     * @return 携带 {@code data} 的实例
     */
    public static CorrelateBranchResult success(Object data) {
        return new CorrelateBranchResult(false, data, null);
    }

    /**
     * 构造一个失败分支结果。
     *
     * @param error 错误描述，不能为 {@code null}
     * @return 携带 {@code error} 的实例
     */
    public static CorrelateBranchResult failure(CorrelateError error) {
        return new CorrelateBranchResult(false, null, error);
    }
}
