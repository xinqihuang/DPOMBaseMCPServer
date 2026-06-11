/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.aom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 单条 AOM 事件/告警，对应 SDK {@code ListEventModel} 的无损投影（§4.1，11 个字段全量）。
 *
 * <p>{@code metadata} / {@code annotations} / {@code attach_rule} / {@code policy}
 * 在上游即为开放键值结构，按 SDK 原样以 Map 承载，不拍平。
 *
 * @param id                  事件 ID
 * @param eventSn             事件序列号
 * @param startsAt            产生时间（UTC 毫秒）
 * @param endsAt              清除（恢复）时间（UTC 毫秒），未恢复时可为 {@code null} 或 0
 * @param arrivesAt           到达系统时间（UTC 毫秒）
 * @param timeout             活动告警自动清除的超时时长（毫秒）
 * @param enterpriseProjectId 企业项目 ID
 * @param metadata            事件详细信息键值对（如级别、资源标识等）
 * @param annotations         附加字段，开放结构
 * @param attachRule          预留的告警规则附加信息，开放结构
 * @param policy              告警策略，开放结构
 * @author h00884391
 * @since 2026-06-11
 */
public record AomEvent(
        @JsonProperty("id") String id,
        @JsonProperty("event_sn") String eventSn,
        @JsonProperty("starts_at") Long startsAt,
        @JsonProperty("ends_at") Long endsAt,
        @JsonProperty("arrives_at") Long arrivesAt,
        @JsonProperty("timeout") Long timeout,
        @JsonProperty("enterprise_project_id") String enterpriseProjectId,
        @JsonProperty("metadata") Map<String, String> metadata,
        @JsonProperty("annotations") Map<String, Object> annotations,
        @JsonProperty("attach_rule") Map<String, Object> attachRule,
        @JsonProperty("policy") Map<String, Object> policy) {
}
