/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.CesNotificationMaskAdapter;
import com.huawei.smartom.agentic.adapter.ces.dto.CesCreateNotificationMaskRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesCreateNotificationMaskResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesDeleteNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesDeleteNotificationMasksResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListNotificationMasksResponse;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CES 告警屏蔽规则（{@code notification mask}）业务编排。
 *
 * <p>对 {@code create_notification_mask} / {@code delete_notification_masks} /
 * {@code list_notification_masks} 三个工具的入参进行规约校验，再委托 adapter 调用 SDK。
 *
 * @author h00884391
 * @since 2026-05-28
 */
@Service
public class CesNotificationMaskService {

    private static final Pattern MASK_NAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_\\-\\u4e00-\\u9fa5]{1,64}$");

    private static final Pattern TIME_PATTERN =
            Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");

    private static final Set<String> ALLOWED_RELATION_TYPES = Set.of(
            "ALARM_RULE", "RESOURCE", "RESOURCE_POLICY_NOTIFICATION",
            "RESOURCE_POLICY_ALARM", "EVENT.SYS");

    private static final Set<String> ALLOWED_LIST_RELATION_TYPES = Set.of(
            "ALARM_RULE", "RESOURCE", "RESOURCE_POLICY_NOTIFICATION",
            "RESOURCE_POLICY_ALARM", "DEFAULT");

    private static final Set<String> ALLOWED_MASK_TYPES =
            Set.of("START_END_TIME", "FOREVER_TIME", "CYCLE_TIME");

    private static final Set<String> ALLOWED_RESOURCE_LEVELS = Set.of("dimension", "product");

    private static final Set<String> ALLOWED_MASK_STATUSES =
            Set.of("MASK_EFFECTIVE", "MASK_INEFFECTIVE");

    private static final Set<String> ALLOWED_SORT_KEYS = Set.of("create_time", "update_time");
    private static final Set<String> ALLOWED_SORT_DIRS = Set.of("ASC", "DESC");

    private static final int DELETE_IDS_MIN = 1;
    private static final int DELETE_IDS_MAX = 100;

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
     * 校验入参后委托 adapter 创建（或更新）告警屏蔽规则。
     *
     * @param request 创建请求，不能为 null
     * @return 创建成功后的屏蔽规则 ID
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public CesCreateNotificationMaskResponse createNotificationMask(CesCreateNotificationMaskRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validateCreate(request);
        return adapter.createNotificationMask(request);
    }

    /**
     * 校验入参后委托 adapter 批量删除告警屏蔽规则。
     *
     * @param request 删除请求，不能为 null
     * @return 删除成功的屏蔽规则 ID 列表
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public CesDeleteNotificationMasksResponse deleteNotificationMasks(CesDeleteNotificationMasksRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        List<String> ids = request.notificationMaskIds();
        if (ids == null || ids.size() < DELETE_IDS_MIN || ids.size() > DELETE_IDS_MAX) {
            throw new InvalidParamException(
                    "notification_mask_ids size must be in [" + DELETE_IDS_MIN + ", "
                            + DELETE_IDS_MAX + "], got: " + (ids == null ? 0 : ids.size()));
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new InvalidParamException("each notification_mask_id must be non-blank");
            }
        }
        return adapter.deleteNotificationMasks(request);
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

    private void validateCreate(CesCreateNotificationMaskRequest request) {
        if (request.maskName() == null || !MASK_NAME_PATTERN.matcher(request.maskName()).matches()) {
            throw new InvalidParamException(
                    "mask_name is required, only [letters/digits/CJK/-/_], length [1, 64]");
        }
        if (request.relationType() == null
                || !ALLOWED_RELATION_TYPES.contains(request.relationType())) {
            throw new InvalidParamException(
                    "relation_type must be one of "
                            + "ALARM_RULE/RESOURCE/RESOURCE_POLICY_NOTIFICATION/"
                            + "RESOURCE_POLICY_ALARM/EVENT.SYS, got: " + request.relationType());
        }
        if (request.maskType() == null || !ALLOWED_MASK_TYPES.contains(request.maskType())) {
            throw new InvalidParamException(
                    "mask_type must be one of START_END_TIME/FOREVER_TIME/CYCLE_TIME, got: "
                            + request.maskType());
        }
        if (request.resourceLevel() != null
                && !ALLOWED_RESOURCE_LEVELS.contains(request.resourceLevel())) {
            throw new InvalidParamException(
                    "resource_level must be 'dimension' or 'product', got: " + request.resourceLevel());
        }
        if ("ALARM_RULE".equals(request.relationType())
                && (request.relationIds() == null || request.relationIds().isEmpty())) {
            throw new InvalidParamException(
                    "relation_ids required when relation_type=ALARM_RULE");
        }
        if ("RESOURCE".equals(request.relationType())
                && (request.resources() == null || request.resources().isEmpty())) {
            throw new InvalidParamException(
                    "resources required when relation_type=RESOURCE");
        }
        if ("START_END_TIME".equals(request.maskType()) || "CYCLE_TIME".equals(request.maskType())) {
            requireDate(request.startDate(), "start_date");
            requireDate(request.endDate(), "end_date");
            requireTime(request.startTime(), "start_time");
            requireTime(request.endTime(), "end_time");
        }
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

    private void requireDate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidParamException(field + " is required for the selected mask_type");
        }
        try {
            LocalDate.parse(value);
        }
        catch (DateTimeParseException ex) {
            throw new InvalidParamException(field + " must be format yyyy-MM-dd, got: " + value);
        }
    }

    private void requireTime(String value, String field) {
        if (value == null || !TIME_PATTERN.matcher(value).matches()) {
            throw new InvalidParamException(field + " must be format HH:mm:ss, got: " + value);
        }
    }
}
