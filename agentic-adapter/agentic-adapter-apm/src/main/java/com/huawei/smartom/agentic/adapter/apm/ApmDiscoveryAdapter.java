/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmEnvMonitorItemsResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmListBusinessResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemViewConfigResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 华为云 APM「CMDB / 监控项 / 视图配置发现」适配器。
 *
 * <p>承担发现链全部只读步骤：
 * <ol>
 *   <li>{@link #listBusiness()} —— 拉取租户全部 APM 应用（business），输出 {@code business_id}（T28）;</li>
 *   <li>{@link #searchApplication(ApmSearchApplicationRequest)} —— 拉取应用下组件/环境与探针计数，
 *       输出 {@code env_id}（T28）;</li>
 *   <li>{@link #showEnvMonitorItems(Long, Long)} —— 拉取 env 下全部监控项及 {@code collector_id}（T23）;</li>
 *   <li>{@link #showMonitorItemViewConfig(Long, Long, Long)} —— 拉取某采集器的真实视图清单（T23）。</li>
 * </ol>
 *
 * <p>与 {@link ApmAlarmAdapter} / {@link ApmTrendAdapter} 并列，共享 {@code ApmClient} Bean。
 *
 * @author h00884391
 * @since 2026-06-10
 */
public interface ApmDiscoveryAdapter {

    /**
     * 拉取当前租户在 APM 注册的全部应用（business）列表。
     *
     * <p>对应 SDK {@code ApmClient.listBusiness(ListBusinessRequest)}，无入参。
     *
     * @return 应用列表（无损覆盖 SDK 全部字段）
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    ApmListBusinessResponse listBusiness();

    /**
     * 搜索指定应用（business）下的组件与环境及其探针情况。
     *
     * <p>对应 SDK {@code ApmClient.searchApplication(SearchApplicationRequest)}。
     * {@code businessId} / {@code region} 为 {@code null} 时回落到配置默认值；
     * header {@code x-business-id} 与 body {@code business_id} 取同一生效值。
     *
     * @param request 请求 DTO，不可为 {@code null}
     * @return 组件/环境列表与探针计数（无损覆盖 SDK 全部字段）
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    ApmSearchApplicationResponse searchApplication(ApmSearchApplicationRequest request);

    /**
     * 拉取指定 env 下全部监控项与采集器类别。
     *
     * <p>对应 SDK {@code ApmClient.showEnvMonitorItems(ShowEnvMonitorItemsRequest)}。
     *
     * @param envId      环境 id（必填）
     * @param businessId APM 业务 id（{@code x-business-id} 头）；为 {@code null} 时回落到配置默认值
     * @return 类别 + 监控项列表（无损覆盖 SDK 全部字段）
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    ApmEnvMonitorItemsResponse showEnvMonitorItems(Long envId, Long businessId);

    /**
     * 拉取某采集器在指定 env 下的视图清单（含 metric_set / field_item_list 等真实字面量）。
     *
     * <p>对应 SDK {@code ApmClient.showMonitorItemViewConfig(ShowMonitorItemViewConfigRequest)}。
     *
     * <p>{@code collectorId} 在 SDK 请求中为 {@link Long}；env-list 中返回的同名字段是
     * {@link Integer}，service 层负责窗口转换，本接口直接以 SDK 类型为准。
     *
     * @param collectorId 采集器 id（必填；env 局部，禁止跨 env 复用）
     * @param envId       环境 id（必填）
     * @param businessId  APM 业务 id；为 {@code null} 时回落到配置默认值
     * @return 视图清单（无损覆盖 SDK 全部字段，含每个 view 的 field_item_list）
     * @throws SmartomException 携带对应的 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    ApmMonitorItemViewConfigResponse showMonitorItemViewConfig(Long collectorId, Long envId, Long businessId);
}
