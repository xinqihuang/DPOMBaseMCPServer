/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmAlarmDataResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 华为云 APM 告警查询适配器。
 *
 * <p>与 {@link ApmTraceAdapter} 同模块、并列；trace / topology 与 alarm 是不同业务域，
 * 分两个接口保持职责清晰，共享同一 {@code ApmClient} Bean。
 *
 * <p>T21 会在此接口上追加 {@code listAlarmNotify} 方法。
 *
 * @author h00884391
 * @since 2026-06-10
 */
public interface ApmAlarmAdapter {

    /**
     * 按 14 个过滤条件查询 APM 告警记录。
     *
     * <p>对应 SDK {@code ApmClient.listAlarmData(ListAlarmDataRequest)} ——
     * {@code POST /v1/apm2/openapi/alarm/data/get-alarm-data-list}。请求需要
     * {@code x-business-id} 头；若 {@link ApmAlarmDataRequest#businessId()} 为 {@code null}
     * 则回落到 {@code huaweicloud.apm-business-id} 配置项。
     *
     * @param request 查询请求（不能为 null）
     * @return 命中告警列表 + 总数（无损覆盖 SDK 27 个字段）
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    ApmAlarmDataResponse listAlarmData(ApmAlarmDataRequest request);
}
