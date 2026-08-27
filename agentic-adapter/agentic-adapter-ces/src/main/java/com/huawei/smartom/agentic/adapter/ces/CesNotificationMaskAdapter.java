/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 华为云 CES 告警屏蔽规则（{@code notification mask}）适配器。
 *
 * <p>仅封装华为云 CES V2 的 {@code ListNotificationMasks} 只读接口。
 *
 * <p>实现类内部完成限流、重试以及异常映射；失败时仅抛出 {@link SmartomException}。
 *
 * @author h00884391
 * @since 2026-05-28
 */
public interface CesNotificationMaskAdapter {

    /**
     * 分页查询告警通知屏蔽规则。
     *
     * @param request 查询请求，不能为 null
     * @return 屏蔽规则列表及总数
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    CesListNotificationMasksResponse listNotificationMasks(CesListNotificationMasksRequest request);
}
