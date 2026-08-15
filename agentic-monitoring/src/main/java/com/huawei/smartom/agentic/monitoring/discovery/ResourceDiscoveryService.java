/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.discovery;

import com.huawei.smartom.agentic.adapter.apm.dto.ApmAppInfo;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmEnvMonitorItemsResponse;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmMonitorItemEntity;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationRequest;
import com.huawei.smartom.agentic.adapter.apm.dto.ApmSearchApplicationResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsListLogStreamsResponse;
import com.huawei.smartom.agentic.adapter.lts.dto.LtsLogStream;
import com.huawei.smartom.agentic.common.exception.InvalidParamException;
import com.huawei.smartom.agentic.monitoring.apm.ApmDiscoveryService;
import com.huawei.smartom.agentic.monitoring.lts.LtsDiscoveryService;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一资源发现编排：把受控锚点规范化为带 provenance 的 ResourceContext，复用现有 APM/LTS 只读发现能力，
 * 对 businessId/envId/appName 做一致性校验；无法完成的映射动态建模为 missing capability，绝不伪造标识。
 *
 * <p>两点硬约束：{@code search_apm_application} 逐页遍历全部结果页（{@code appTotalCount} 仅作提示，
 * 空页/短页/最大页数才是可靠终止边界），避免目标落在后续页时假阴性；同名 identifier 的 USER_PROVIDED
 * 与上游返回值做统一收敛——同值去重、异值标记冲突并保留双方 provenance，绝不静默视为无冲突 EXACT。
 *
 * @author h00884391
 * @since 2026-08-16
 */
@Service
public class ResourceDiscoveryService {

    private static final String USER_PROVIDED = "USER_PROVIDED";
    private static final String TOOL_LTS = "list_lts_log_streams";
    private static final String TOOL_APM = "search_apm_application";
    private static final String TOOL_MONITOR = "show_env_monitor_items";
    private static final String API_LTS = "LtsListLogStreams";
    private static final String API_APM = "ApmSearchApplication";
    private static final String API_MONITOR = "ApmShowEnvMonitorItems";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 20;

    private final ApmDiscoveryService apmDiscovery;
    private final LtsDiscoveryService ltsDiscovery;

    /**
     * 构造统一资源发现编排。
     *
     * @param apmDiscovery APM 只读发现服务
     * @param ltsDiscovery LTS 只读发现服务
     */
    public ResourceDiscoveryService(ApmDiscoveryService apmDiscovery, LtsDiscoveryService ltsDiscovery) {
        this.apmDiscovery = apmDiscovery;
        this.ltsDiscovery = ltsDiscovery;
    }

    /**
     * 组装规范化 ResourceContext。
     *
     * @param request 受控锚点，不可为 null 且至少一个非空
     * @return ResourceContext，不含 null
     * @throws InvalidParamException 未提供任何锚点时抛出
     */
    public ResourceContext discover(DiscoveryRequest request) {
        if (request == null || request.isEmpty()) {
            throw new InvalidParamException("at least one discovery anchor is required");
        }
        List<ResourceIdentifier> identifiers = new ArrayList<>();
        List<ResourceCandidate> candidates = new ArrayList<>();
        List<MissingCapability> missing = new ArrayList<>();
        long now = System.currentTimeMillis();
        addUserProvided(identifiers, request, now);
        resolveLts(request, identifiers, candidates, missing, now);
        resolveApm(request, identifiers, candidates, missing, now);
        resolveMonitorItems(request, identifiers, candidates, missing, now);
        addDynamicMissing(request, identifiers, missing);
        reconcile(identifiers, missing);
        return new ResourceContext(identifiers, missing, candidates);
    }

    /**
     * 返回无法唯一映射的候选列表。
     *
     * @param request 受控锚点
     * @return 候选列表，可能为空
     */
    public List<ResourceCandidate> resolve(DiscoveryRequest request) {
        return discover(request).candidates();
    }

    private void addUserProvided(List<ResourceIdentifier> identifiers, DiscoveryRequest request, long now) {
        addAnchor(identifiers, "region", request.region(), now);
        addAnchor(identifiers, "service_name", request.serviceName(), now);
        addAnchor(identifiers, "apm_app_name", request.appName(), now);
        addAnchor(identifiers, "cluster_id", request.clusterId(), now);
        addAnchor(identifiers, "namespace", request.namespace(), now);
        addAnchor(identifiers, "pod_name", request.podName(), now);
        addAnchor(identifiers, "workload_name", request.workloadName(), now);
        addAnchor(identifiers, "apm_business_id", stringOf(request.businessId()), now);
        addAnchor(identifiers, "apm_env_id", stringOf(request.envId()), now);
        addAnchor(identifiers, "apm_instance_id", stringOf(request.instanceId()), now);
        addAnchor(identifiers, "apm_monitor_item_id", stringOf(request.monitorItemId()), now);
        addAnchor(identifiers, "ip_address", request.ipAddress(), now);
        addAnchor(identifiers, "alarm_id", request.alarmId(), now);
        addAnchor(identifiers, "trace_id", request.traceId(), now);
        addAnchor(identifiers, "lts_log_group_id", request.logGroupId(), now);
        addAnchor(identifiers, "lts_log_group_name", request.logGroupName(), now);
        addAnchor(identifiers, "lts_log_stream_id", request.logStreamId(), now);
        addAnchor(identifiers, "lts_log_stream_name", request.logStreamName(), now);
    }

    private void resolveLts(DiscoveryRequest request, List<ResourceIdentifier> identifiers,
            List<ResourceCandidate> candidates, List<MissingCapability> missing, long now) {
        if (request.logGroupName() == null && request.logStreamName() == null) {
            return;
        }
        LtsListLogStreamsResponse response = ltsDiscovery.listLogStreams(request.logGroupName(),
                request.logStreamName());
        List<LtsLogStream> streams = response.logStreams() == null ? List.of() : response.logStreams();
        if (streams.size() == 1) {
            addUpstream(identifiers, "lts_log_group_id", streams.get(0).logGroupId(), TOOL_LTS, API_LTS, now);
            addUpstream(identifiers, "lts_log_stream_id", streams.get(0).logStreamId(), TOOL_LTS, API_LTS, now);
        }
        else if (streams.size() > 1) {
            for (LtsLogStream stream : streams) {
                candidates.add(new ResourceCandidate("lts_log_stream_id", stream.logStreamId(), MatchType.NAME_MATCH,
                        TOOL_LTS, API_LTS, "提供精确 log_stream_name 或 log_stream_id 以消除歧义"));
            }
        }
        else {
            missing.add(new MissingCapability("lts_log_stream_id",
                    "按提供的日志组/流名未匹配到任何日志流", "提供真实的 log_group_id 或精确日志组名"));
        }
    }

    private void resolveApm(DiscoveryRequest request, List<ResourceIdentifier> identifiers,
            List<ResourceCandidate> candidates, List<MissingCapability> missing, long now) {
        if (request.businessId() == null && request.appName() == null && request.serviceName() == null) {
            return;
        }
        List<ApmAppInfo> matched = filterApps(searchAllApps(request), request.envId(), request.appName());
        if (request.businessId() != null && matched.size() == 1) {
            ApmAppInfo app = matched.get(0);
            addUpstream(identifiers, "apm_app_name", app.appName(), TOOL_APM, API_APM, now);
            addUpstream(identifiers, "apm_env_id", stringOf(app.envId()), TOOL_APM, API_APM, now);
            addUpstream(identifiers, "apm_app_id", stringOf(app.appId()), TOOL_APM, API_APM, now);
        }
        else if (matched.size() > 1) {
            for (ApmAppInfo app : matched) {
                candidates.add(new ResourceCandidate("apm_app_name", app.appName(), MatchType.NAME_MATCH,
                        TOOL_APM, API_APM, nextStepForApm(request)));
            }
        }
        else {
            missing.add(new MissingCapability("apm_app_name",
                    "businessId 与 envId/appName 组合未匹配到唯一组件", "提供一致的 business_id 与 env_id"));
        }
        addInstanceVerificationGap(request, missing);
    }

    private void resolveMonitorItems(DiscoveryRequest request, List<ResourceIdentifier> identifiers,
            List<ResourceCandidate> candidates, List<MissingCapability> missing, long now) {
        if (request.envId() == null) {
            return;
        }
        ApmEnvMonitorItemsResponse response = apmDiscovery.getEnvMonitorItems(request.envId(),
                request.businessId());
        List<ApmMonitorItemEntity> items = response.monitorItemInfoList() == null
                ? List.of() : response.monitorItemInfoList();
        List<ApmMonitorItemEntity> matched = request.monitorItemId() == null ? items
                : items.stream().filter(item -> request.monitorItemId().equals(item.monitorItemId())).toList();
        if (matched.size() == 1) {
            addUpstream(identifiers, "apm_monitor_item_id", stringOf(matched.get(0).monitorItemId()),
                    TOOL_MONITOR, API_MONITOR, now);
        }
        else if (matched.size() > 1) {
            for (ApmMonitorItemEntity item : matched) {
                candidates.add(new ResourceCandidate("apm_monitor_item_id", stringOf(item.monitorItemId()),
                        MatchType.NAME_MATCH, TOOL_MONITOR, API_MONITOR, "提供 monitor_item_id 以消除歧义"));
            }
        }
        else {
            missing.add(new MissingCapability("apm_monitor_item_id",
                    "envId 下未匹配到 monitor_item_id", "提供与该 env 一致的 monitor_item_id"));
        }
    }

    private void addDynamicMissing(DiscoveryRequest request, List<ResourceIdentifier> identifiers,
            List<MissingCapability> missing) {
        boolean cceAnchored = request.clusterId() != null || request.namespace() != null
                || request.podName() != null || request.workloadName() != null;
        if (cceAnchored && !hasIdentifier(identifiers, "node_name")) {
            missing.add(new MissingCapability("node_name", "现有工具无 CCE 节点清单只读能力",
                    "提供 CCE 节点名或集群节点清单来源"));
            missing.add(new MissingCapability("ecs_instance_id", "现有工具无法从 CCE 节点映射到 ECS instance_id",
                    "提供 ECS instance_id 或节点 IP 的权威来源"));
        }
        boolean apmAnchored = request.businessId() != null || request.envId() != null || request.appName() != null
                || request.instanceId() != null || request.monitorItemId() != null || request.alarmId() != null;
        if (apmAnchored && request.logGroupId() == null && request.logStreamId() == null
                && request.logGroupName() == null && request.logStreamName() == null) {
            missing.add(new MissingCapability("lts_log_group_id",
                    "现有工具无 APM→LTS 日志组/流的确定性映射", "提供与 APM 组件对应的 LTS 日志组/流标识"));
        }
        boolean ltsAnchored = request.logGroupId() != null || request.logStreamId() != null
                || request.logGroupName() != null || request.logStreamName() != null;
        if (ltsAnchored && request.businessId() == null && request.envId() == null && request.appName() == null
                && request.serviceName() == null) {
            missing.add(new MissingCapability("apm_business_id",
                    "现有工具无 LTS→APM 的确定性映射", "提供 business_id 或服务/app 名以关联 APM"));
        }
        if (request.alarmId() != null && request.businessId() == null && request.envId() == null
                && request.instanceId() == null && request.ipAddress() == null && request.monitorItemId() == null) {
            missing.add(new MissingCapability("apm_instance_id",
                    "现有 discovery 工具无法仅凭 alarmId 解析实例上下文", "配合 business_id/env_id/instance_id/IP 使用"));
        }
    }

    private void addInstanceVerificationGap(DiscoveryRequest request, List<MissingCapability> missing) {
        if (request.instanceId() != null) {
            return;
        }
        boolean needsInstance = request.monitorItemId() != null || request.ipAddress() != null;
        if (needsInstance) {
            missing.add(new MissingCapability("apm_instance_id",
                    "现有发现工具不返回 APM 实例 id，无法验证/补全实例级标识", "提供 APM 实例 id"));
        }
    }

    /**
     * 按真实分页字段遍历 {@code search_apm_application} 的全部结果页。
     *
     * <p>可靠终止边界只有三个：空页立即停止；本页不足 {@code PAGE_SIZE} 视为最后一页；最多
     * {@code MAX_PAGES} 页（页码每轮严格 +1），任一分支都保证退出，不会因分页元数据异常而无限循环。
     * {@code appTotalCount} 仅作提示、不参与终止判断——它可能偏小，满页时不能仅凭累计数达到该值就停止，
     * 否则会漏掉后续页的目标。
     *
     * @param request 受控锚点，提供 businessId/region 与可选 keyword
     * @return 全部页拼接后的组件列表
     */
    private List<ApmAppInfo> searchAllApps(DiscoveryRequest request) {
        String keyword = request.appName() != null ? request.appName() : request.serviceName();
        List<ApmAppInfo> all = new ArrayList<>();
        int page = 1;
        while (page <= MAX_PAGES) {
            ApmSearchApplicationResponse response = apmDiscovery.searchApplication(
                    new ApmSearchApplicationRequest(request.businessId(), request.region(), page, PAGE_SIZE, keyword));
            List<ApmAppInfo> pageApps = response.appInfoList() == null ? List.of() : response.appInfoList();
            if (pageApps.isEmpty()) {
                break;
            }
            all.addAll(pageApps);
            if (pageApps.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return all;
    }

    /**
     * 收敛同名 identifier：同值去重为单一规范值，异值标记冲突并保留双方 provenance。
     *
     * @param identifiers 待收敛的标识列表（原地替换）
     * @param missing     冲突产生的 missing capability 追加到此列表
     */
    private void reconcile(List<ResourceIdentifier> identifiers, List<MissingCapability> missing) {
        Map<String, List<ResourceIdentifier>> grouped = new LinkedHashMap<>();
        for (ResourceIdentifier id : identifiers) {
            grouped.computeIfAbsent(id.name(), key -> new ArrayList<>()).add(id);
        }
        List<ResourceIdentifier> reconciled = new ArrayList<>();
        for (Map.Entry<String, List<ResourceIdentifier>> entry : grouped.entrySet()) {
            List<ResourceIdentifier> ids = entry.getValue();
            List<String> values = ids.stream().map(ResourceIdentifier::value).distinct().toList();
            if (values.size() <= 1) {
                reconciled.add(canonical(ids));
            }
            else {
                for (ResourceIdentifier id : ids) {
                    reconciled.add(markAmbiguous(id));
                }
                missing.add(new MissingCapability(entry.getKey(),
                        "同名标识存在冲突值：" + String.join(", ", values),
                        "核对 " + entry.getKey() + " 的权威来源以消除冲突"));
            }
        }
        identifiers.clear();
        identifiers.addAll(reconciled);
    }

    /**
     * 同值 identifier 选取单一规范值：优先上游验证过的来源，否则保留 USER_PROVIDED。
     *
     * @param ids 同名同值的标识列表，不可为空
     * @return 单一规范标识
     */
    private ResourceIdentifier canonical(List<ResourceIdentifier> ids) {
        return ids.stream().filter(id -> !USER_PROVIDED.equals(id.sourceApi())).findFirst().orElse(ids.get(0));
    }

    /**
     * 把标识标记为歧义（冲突）态，保留其原始 provenance。
     *
     * @param id 原始标识
     * @return 标记 ambiguous=true 的副本
     */
    private ResourceIdentifier markAmbiguous(ResourceIdentifier id) {
        return new ResourceIdentifier(id.name(), id.value(), id.sourceTool(), id.sourceApi(), id.observedAt(),
                id.kind(), true);
    }

    private List<ApmAppInfo> filterApps(List<ApmAppInfo> apps, Long envId, String appName) {
        return apps.stream()
                .filter(app -> envId == null || envId.equals(app.envId()))
                .filter(app -> appName == null || appName.equals(app.appName()))
                .toList();
    }

    private String nextStepForApm(DiscoveryRequest request) {
        if (request.envId() == null) {
            return "提供 env_id 以收敛到唯一组件";
        }
        if (request.appName() == null) {
            return "提供 app_name 以收敛到唯一组件";
        }
        return "business_id 与 env_id/app_name 存在多个组件，需人工核对";
    }

    private boolean hasIdentifier(List<ResourceIdentifier> identifiers, String name) {
        return identifiers.stream().anyMatch(id -> id.name().equals(name));
    }

    private void addAnchor(List<ResourceIdentifier> identifiers, String name, String value, long now) {
        if (value != null && !value.isBlank()) {
            identifiers.add(new ResourceIdentifier(name, value, USER_PROVIDED, USER_PROVIDED, now,
                    IdentifierKind.EXACT, false));
        }
    }

    private void addUpstream(List<ResourceIdentifier> identifiers, String name, String value, String tool,
            String api, long now) {
        if (value != null && !value.isBlank()) {
            identifiers.add(new ResourceIdentifier(name, value, tool, api, now, IdentifierKind.EXACT, false));
        }
    }

    private String stringOf(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
