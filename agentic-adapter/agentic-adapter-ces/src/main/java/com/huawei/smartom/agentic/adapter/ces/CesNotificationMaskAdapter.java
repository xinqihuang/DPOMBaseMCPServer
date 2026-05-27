/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesCreateNotificationMaskRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesCreateNotificationMaskResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesDeleteNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesDeleteNotificationMasksResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 华为云 CES 告警屏蔽规则（{@code notification mask}）适配器。
 *
 * <p>对应华为云 CES V2 接口：
 * <ul>
 *   <li>{@code BatchUpdateNotificationMasks} — 创建/更新屏蔽规则</li>
 *   <li>{@code BatchDeleteNotificationMasks} — 批量删除屏蔽规则</li>
 *   <li>{@code ListNotificationMasks} — 分页查询屏蔽规则</li>
 * </ul>
 *
 * <p>实现类内部完成限流、重试以及异常映射；失败时仅抛出 {@link SmartomException}。
 *
 * @author h00884391
 * @since 2026-05-28
 */
public interface CesNotificationMaskAdapter {

    /**
     * 创建（或更新）一条告警通知屏蔽规则。常用于变更前屏蔽告警，避免误告警。
     *
     * @param request 创建请求，不能为 null
     * @return 创建成功后的屏蔽规则 ID 与关联 ID 列表
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    CesCreateNotificationMaskResponse createNotificationMask(CesCreateNotificationMaskRequest request);

    /**
     * 批量删除告警通知屏蔽规则。
     *
     * @param request 删除请求，不能为 null，{@code notificationMaskIds} 长度 [1, 100]
     * @return 删除成功的屏蔽规则 ID 列表
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    CesDeleteNotificationMasksResponse deleteNotificationMasks(CesDeleteNotificationMasksRequest request);

    /**
     * 分页查询告警通知屏蔽规则。
     *
     * @param request 查询请求，不能为 null
     * @return 屏蔽规则列表及总数
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    CesListNotificationMasksResponse listNotificationMasks(CesListNotificationMasksRequest request);
}
