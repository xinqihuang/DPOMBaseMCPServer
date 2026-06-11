/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单条组件/环境信息，对应 SDK {@code AppInfo} 的无损投影（§4.1，7 字段全量）。
 *
 * <p>探针计数（在线/手动停止/离线）是运行态信号，随探针上下线实时变化。
 *
 * @param envName      环境名称
 * @param envId        环境 id，喂给 {@code show_env_monitor_items}（T23 链入口）
 * @param appName      组件名称
 * @param appId        组件 id
 * @param onlineCount  在线探针数
 * @param disableCount 手动停止探针数
 * @param offlineCount 离线探针数
 * @author h00884391
 * @since 2026-06-11
 */
public record ApmAppInfo(
        @JsonProperty("env_name") String envName,
        @JsonProperty("env_id") Long envId,
        @JsonProperty("app_name") String appName,
        @JsonProperty("app_id") Long appId,
        @JsonProperty("online_count") Integer onlineCount,
        @JsonProperty("disable_count") Integer disableCount,
        @JsonProperty("offline_count") Integer offlineCount) {
}
