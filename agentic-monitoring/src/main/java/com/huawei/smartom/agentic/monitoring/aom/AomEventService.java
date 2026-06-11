/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.aom;

import com.huawei.smartom.agentic.adapter.aom.AomEventAdapter;
import com.huawei.smartom.agentic.adapter.aom.dto.AomEventMetadataRelation;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsRequest;
import com.huawei.smartom.agentic.adapter.aom.dto.AomListEventsResponse;
import com.huawei.smartom.agentic.adapter.aom.dto.AomPatterns;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.common.validation.Validations;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * AOM 事件/告警查询的业务编排（T27）。
 *
 * <p>按 {@code docs/specs/tools/list_aom_events.md} §4 校验入参：
 * <ul>
 *   <li>{@code time_range} 必填且符合 {@code startMs.endMs.durationMin} 格式</li>
 *   <li>{@code step} / {@code limit} 为正数</li>
 *   <li>提供 {@code sort_order} 时 {@code sort_order_by} 必填（SDK 约束）</li>
 *   <li>{@code metadata_relation} 的 key 非空、relation 取值在 {@code AND/OR/NOT} 内</li>
 * </ul>
 * {@code type} 已通过受控枚举强类型，不在此校验取值集合。告警为实时数据，不做缓存。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@Service
public class AomEventService {

    private static final Set<String> ALLOWED_ORDERS = Set.of("asc", "desc");
    private static final Set<String> ALLOWED_RELATIONS = Set.of("AND", "OR", "NOT");

    private final AomEventAdapter adapter;

    /**
     * 构造一个由指定 adapter 支撑的 {@code AomEventService}。
     *
     * @param adapter 执行实际 SDK 调用的 AOM 事件 adapter
     */
    public AomEventService(AomEventAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * 校验入参后委托 adapter 执行查询。
     *
     * @param request 请求 DTO，不能为 null
     * @return 事件/告警列表响应
     * @throws InvalidParamException 入参不符合规约时抛出
     */
    public AomListEventsResponse listEvents(AomListEventsRequest request) {
        if (request == null) {
            throw new InvalidParamException("request must not be null");
        }
        validate(request);
        return adapter.listEvents(request);
    }

    private void validate(AomListEventsRequest request) {
        Validations.requireNonBlank(request.timeRange(), "time_range");
        if (!AomPatterns.TIME_RANGE.matcher(request.timeRange()).matches()) {
            throw new InvalidParamException(
                    "time_range format invalid, expected 'startMs.endMs.durationMin' "
                            + "(use -1 for server-side calculation), got: " + request.timeRange());
        }
        if (request.step() != null && request.step() <= 0) {
            throw new InvalidParamException("step must be positive millis, got: " + request.step());
        }
        if (request.limit() != null && request.limit() < 1) {
            throw new InvalidParamException("limit must be >= 1, got: " + request.limit());
        }
        validateSort(request);
        validateMetadataRelation(request.metadataRelation());
    }

    private void validateSort(AomListEventsRequest request) {
        if (request.sortOrder() != null) {
            if (!ALLOWED_ORDERS.contains(request.sortOrder())) {
                throw new InvalidParamException(
                        "sort_order must be 'asc' or 'desc', got: " + request.sortOrder());
            }
            if (request.sortOrderBy() == null || request.sortOrderBy().isEmpty()) {
                throw new InvalidParamException(
                        "sort_order_by is required when sort_order is provided");
            }
        }
        if (request.sortOrderBy() != null) {
            for (String field : request.sortOrderBy()) {
                if (Validations.isBlank(field)) {
                    throw new InvalidParamException("sort_order_by entries must be non-blank");
                }
            }
        }
    }

    private void validateMetadataRelation(List<AomEventMetadataRelation> relations) {
        if (relations == null) {
            return;
        }
        for (int idx = 0; idx < relations.size(); idx++) {
            AomEventMetadataRelation relation = relations.get(idx);
            if (relation == null) {
                throw new InvalidParamException(
                        "metadata_relation[" + idx + "] must not be null");
            }
            Validations.requireNonBlank(relation.key(), "metadata_relation[" + idx + "].key");
            if (relation.relation() != null && !ALLOWED_RELATIONS.contains(relation.relation())) {
                throw new InvalidParamException(
                        "metadata_relation[" + idx + "].relation must be one of AND/OR/NOT, got: "
                                + relation.relation());
            }
        }
    }
}
