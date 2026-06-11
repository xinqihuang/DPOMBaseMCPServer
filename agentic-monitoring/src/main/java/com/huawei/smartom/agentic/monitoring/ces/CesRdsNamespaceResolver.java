/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.ces;

import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsRequest;
import com.huawei.smartom.agentic.adapter.ces.dto.CesListMetricsResponse;
import com.huawei.smartom.agentic.adapter.ces.dto.CesMetricDimension;
import com.huawei.smartom.agentic.adapter.ces.dto.CesNamespace;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SYS_RDS 形态 fallback 的命名空间解析器（T24，仅对 {@code SYS.RDS} 生效的显式特例）。
 *
 * <p>背景：RDS for MySQL 按部署形态分裂为两个 CES namespace——主备/单机是 {@code SYS.RDS}，
 * 集群版是 {@code SYS.RDS_MYSQL_CLUSTER}；而 Agent 无法从告警 payload 判定部署形态，
 * 猜错 namespace 时上游返回空数据而非报错，会无声带偏诊断。因此 Agent 的世界里 RDS
 * 只有 {@link CesNamespace#SYS_RDS} 一个值，形态判定收敛到本类。
 *
 * <p>探测信号用 {@code list_ces_metrics} 的<strong>存在性</strong>，而非查询数据是否为空：
 * 查询为空有歧义（可能是 namespace 错，也可能该时间窗真没数据），而「该实例在某 namespace
 * 下是否注册了任何指标」是与时间窗无关的干净信号。探测经由 {@link CesMetricsService}
 * 的 Caffeine 缓存（TTL 1 天），命中后几乎零成本。
 *
 * <p><strong>禁止泛化</strong>：本 fallback 只处理 SYS_RDS 这一个已知的双 namespace 分裂，
 * 不要扩展成「任何 namespace 查不到就乱试」的通用机制——那会把参数错误也静默吞掉。
 *
 * @author h00884391
 * @since 2026-06-11
 */
@Component
class CesRdsNamespaceResolver {

    /** Agent 可见的 RDS 命名空间（{@code SYS.RDS}）。 */
    static final String RDS_NAMESPACE = CesNamespace.SYS_RDS.getValue();

    /**
     * RDS for MySQL 集群版命名空间。service 内部常量——不进 {@link CesNamespace} 枚举、
     * 不进工具 JSON Schema（见任务卡 T24 纪律 3）。
     */
    static final String RDS_MYSQL_CLUSTER_NAMESPACE = "SYS.RDS_MYSQL_CLUSTER";

    /** 探测仅关心存在性，取最小分页即可。 */
    private static final int PROBE_LIMIT = 1;

    private static final Logger LOG = LoggerFactory.getLogger(CesRdsNamespaceResolver.class);

    private final CesMetricsService metricsService;

    /**
     * 构造解析器。
     *
     * @param metricsService 指标定义查询服务，探测调用经其 Caffeine 缓存
     */
    CesRdsNamespaceResolver(CesMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * 判断给定命名空间是否需要走 RDS 形态 fallback。
     *
     * @param namespace 请求侧命名空间字面量，可为 {@code null}
     * @return {@code true} 当且仅当取值为 {@code SYS.RDS}
     */
    static boolean appliesTo(String namespace) {
        return RDS_NAMESPACE.equals(namespace);
    }

    /**
     * 按实例维度探测实际可取数的 RDS 命名空间。
     *
     * @param dimension 实例维度（取查询项的第一个维度，通常为实例 ID 维度），不可为 {@code null}
     * @return {@code SYS.RDS}（主备/单机命中）或 {@code SYS.RDS_MYSQL_CLUSTER}（集群版命中）
     * @throws InvalidParamException 两个 namespace 下均无该实例的指标定义时抛出——
     *         通常意味着维度值（实例 ID）不存在或填错
     */
    String resolve(CesMetricDimension dimension) {
        if (dimension == null) {
            throw new InvalidParamException("dimension is required for SYS.RDS namespace probing");
        }
        if (hasAnyMetric(RDS_NAMESPACE, dimension)) {
            return RDS_NAMESPACE;
        }
        if (hasAnyMetric(RDS_MYSQL_CLUSTER_NAMESPACE, dimension)) {
            LOG.info("SYS.RDS fallback hit, dimension {}={} resolved to {}",
                    dimension.name(), dimension.value(), RDS_MYSQL_CLUSTER_NAMESPACE);
            return RDS_MYSQL_CLUSTER_NAMESPACE;
        }
        throw new InvalidParamException(
                "no metric definitions found under SYS.RDS or SYS.RDS_MYSQL_CLUSTER for dimension "
                        + dimension.name() + "=" + dimension.value()
                        + "; verify the instance id via list_ces_metrics before querying");
    }

    private boolean hasAnyMetric(String namespace, CesMetricDimension dimension) {
        CesListMetricsResponse resp = metricsService.listMetrics(new CesListMetricsRequest(
                namespace, null, dimension.name(), dimension.value(), PROBE_LIMIT, null, null));
        return resp != null && resp.metrics() != null && !resp.metrics().isEmpty();
    }
}
