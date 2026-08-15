/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.discovery;

/**
 * 资源发现请求的受控锚点（全部可选，至少其一）。
 *
 * @param region         区域，如 {@code cn-north-9}
 * @param serviceName    服务名
 * @param appName        APM 组件名
 * @param clusterId      CCE 集群 id
 * @param namespace      CCE 命名空间
 * @param podName        Pod 名
 * @param workloadName   工作负载名
 * @param businessId     APM 业务 id
 * @param envId          APM 环境 id
 * @param instanceId     APM 实例 id
 * @param monitorItemId  APM 监控项 id
 * @param ipAddress      实例 IP
 * @param alarmId        APM 告警记录 id
 * @param traceId        APM trace id
 * @param logGroupId     LTS 日志组 id
 * @param logGroupName   LTS 日志组名
 * @param logStreamId    LTS 日志流 id
 * @param logStreamName  LTS 日志流名
 *
 * @author h00884391
 * @since 2026-08-16
 */
public record DiscoveryRequest(String region, String serviceName, String appName, String clusterId,
        String namespace, String podName, String workloadName, Long businessId, Long envId,
        Long instanceId, Long monitorItemId, String ipAddress, String alarmId, String traceId,
        String logGroupId, String logGroupName, String logStreamId, String logStreamName) {

    /**
     * 判断是否未提供任何锚点。
     *
     * @return true 表示所有锚点均为空
     */
    public boolean isEmpty() {
        return region == null && serviceName == null && appName == null && clusterId == null
                && namespace == null && podName == null && workloadName == null && businessId == null
                && envId == null && instanceId == null && monitorItemId == null && ipAddress == null
                && alarmId == null && traceId == null && logGroupId == null && logGroupName == null
                && logStreamId == null && logStreamName == null;
    }
}
