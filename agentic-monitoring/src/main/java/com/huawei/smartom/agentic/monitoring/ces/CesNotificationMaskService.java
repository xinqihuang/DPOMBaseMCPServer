/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesNotificationMaskAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * CES 告警屏蔽规则（{@code notification mask}）业务编排。
 *
 * <p>仅对 {@code list_notification_masks} 只读工具的入参进行规约校验，再委托 adapter 调用 SDK。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Service
public class CesNotificationMaskService {

    private static final Set<String> ALLOWED_LIST_RELATION_TYPES = Set.of(
            "ALARM_RULE", "RESOURCE", "RESOURCE_POLICY_NOTIFICATION",
            "RESOURCE_POLICY_ALARM", "DEFAULT");

    private static final Set<String> ALLOWED_RESOURCE_LEVELS = Set.of("dimension", "product");

    private static final Set<String> ALLOWED_MASK_STATUSES =
            Set.of("MASK_EFFECTIVE", "MASK_INEFFECTIVE");

    private static final Set<String> ALLOWED_SORT_KEYS = Set.of("create_time", "update_time");
    private static final Set<String> ALLOWED_SORT_DIRS = Set.of("ASC", "DESC");

    private static final int LIST_LIMIT_MIN = 1;
    private static final int LIST_LIMIT_MAX = 100;
    private static final int LIST_OFFSET_MIN = 0;
    private static final int LIST_OFFSET_MAX = 10_000;

    private final CesNotificationMaskAdapter adapter;

    /**
     * 构造一个由指定 adapter 支撑的 {@code CesNotificationMaskService}。
     *
     * @param adapter 执行实际 SDK 调用的 CES 告警屏蔽适配器
     */
    public CesNotificationMaskService(CesNotificationMaskAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参后委托 adapter 分页查询告警屏蔽规则。
     *
     * @param request 查询请求，不能为 null
     * @return 屏蔽规则列表
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public CesListNotificationMasksResponse listNotificationMasks(CesListNotificationMasksRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validateList(request);
        return adapter.listNotificationMasks(request);
    }

    private void validateList(CesListNotificationMasksRequest request) {
        if (request.offset() == null
                || request.offset() < LIST_OFFSET_MIN
                || request.offset() > LIST_OFFSET_MAX) {
            throw new InvalidParamException(
                    "offset must be in [" + LIST_OFFSET_MIN + ", " + LIST_OFFSET_MAX + "]");
        }
        if (request.limit() == null
                || request.limit() < LIST_LIMIT_MIN
                || request.limit() > LIST_LIMIT_MAX) {
            throw new InvalidParamException(
                    "limit must be in [" + LIST_LIMIT_MIN + ", " + LIST_LIMIT_MAX + "]");
        }
        if (request.sortKey() != null && !ALLOWED_SORT_KEYS.contains(request.sortKey())) {
            throw new InvalidParamException(
                    "sort_key must be create_time/update_time, got: " + request.sortKey());
        }
        if (request.sortDir() != null && !ALLOWED_SORT_DIRS.contains(request.sortDir())) {
            throw new InvalidParamException(
                    "sort_dir must be ASC/DESC, got: " + request.sortDir());
        }
        if (request.relationType() != null
                && !ALLOWED_LIST_RELATION_TYPES.contains(request.relationType())) {
            throw new InvalidParamException(
                    "relation_type must be one of "
                            + "ALARM_RULE/RESOURCE/RESOURCE_POLICY_NOTIFICATION/"
                            + "RESOURCE_POLICY_ALARM/DEFAULT, got: " + request.relationType());
        }
        if (request.resourceLevel() != null
                && !ALLOWED_RESOURCE_LEVELS.contains(request.resourceLevel())) {
            throw new InvalidParamException(
                    "resource_level must be 'dimension' or 'product', got: " + request.resourceLevel());
        }
        if (request.maskStatus() != null
                && !ALLOWED_MASK_STATUSES.contains(request.maskStatus())) {
            throw new InvalidParamException(
                    "mask_status must be MASK_EFFECTIVE/MASK_INEFFECTIVE, got: " + request.maskStatus());
        }
    }

}
