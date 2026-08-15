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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一资源发现编排测试：无锚点拒绝、instanceId 保留、appId 不冒充 instanceId、envId 收敛、
 * 已提供字段不重复提示、动态缺口、provenance 分离、多候选不静默、分页遍历、同名标识冲突收敛。
 *
 * @author h00884391
 * @since 2026-08-16
 */
class ResourceDiscoveryServiceTest {

    private ApmDiscoveryService apmDiscovery;
    private LtsDiscoveryService ltsDiscovery;
    private ResourceDiscoveryService service;

    @BeforeEach
    void setUp() {
        apmDiscovery = mock(ApmDiscoveryService.class);
        ltsDiscovery = mock(LtsDiscoveryService.class);
        service = new ResourceDiscoveryService(apmDiscovery, ltsDiscovery);
    }

    @Test
    @DisplayName("无锚点拒绝")
    void emptyRequestRejected() {
        Throwable throwable = catchThrowable(() -> service.discover(request(null, null, null, null)));

        assertThat(throwable).isInstanceOf(InvalidParamException.class);
    }

    @Test
    @DisplayName("instanceId 作为 USER_PROVIDED 锚点保留")
    void instanceIdPreservedAsUserProvided() {
        ResourceContext context = service.discover(request(null, null, 222L, null));

        assertThat(context.identifiers()).anyMatch(id ->
                id.name().equals("apm_instance_id") && id.value().equals("222")
                        && "USER_PROVIDED".equals(id.sourceTool()));
    }

    @Test
    @DisplayName("appId 不冒充 instanceId")
    void appIdNotMappedToInstanceId() {
        when(apmDiscovery.searchApplication(any())).thenReturn(new ApmSearchApplicationResponse(
                List.of(app("env-a", 1L, "svc-a", 999L)), 1, null));

        ResourceContext context = service.discover(request(111092L, null, null, null));

        assertThat(context.identifiers()).anyMatch(id ->
                id.name().equals("apm_app_id") && id.value().equals("999"));
        assertThat(context.identifiers()).noneMatch(id -> id.name().equals("apm_instance_id"));
    }

    @Test
    @DisplayName("envId 收敛到唯一组件")
    void envIdConvergesToUniqueApp() {
        when(apmDiscovery.searchApplication(any())).thenReturn(new ApmSearchApplicationResponse(
                List.of(app("env-a", 1L, "svc-a", 100L), app("env-b", 2L, "svc-b", 101L)), 2, null));
        when(apmDiscovery.getEnvMonitorItems(any(), any()))
                .thenReturn(new ApmEnvMonitorItemsResponse(List.of(), List.of()));

        ResourceContext context = service.discover(request(111092L, 2L, null, null));

        assertThat(context.identifiers()).anyMatch(id ->
                id.name().equals("apm_app_name") && id.value().equals("svc-b")
                        && "ApmSearchApplication".equals(id.sourceApi()));
        assertThat(context.candidates()).isEmpty();
    }

    @Test
    @DisplayName("多候选不静默且不重复提示已提供字段")
    void multipleCandidatesNotSilentAndNoRepeatedHint() {
        when(apmDiscovery.searchApplication(any())).thenReturn(new ApmSearchApplicationResponse(
                List.of(app("env-a", 1L, "svc-a", 100L), app("env-b", 2L, "svc-b", 101L)), 2, null));

        ResourceContext context = service.discover(request(111092L, null, null, null));

        assertThat(context.candidates()).hasSize(2);
        assertThat(context.candidates()).allMatch(candidate ->
                candidate.matchType() == MatchType.NAME_MATCH && candidate.nextStep() != null);
    }

    @Test
    @DisplayName("动态缺口：CCE 锚点 → 节点/ECS 缺口")
    void dynamicMissingForCce() {
        ResourceContext context = service.discover(new DiscoveryRequest(null, null, null, "cluster-1", null,
                null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(context.missingCapabilities()).extracting(MissingCapability::target)
                .contains("node_name", "ecs_instance_id");
        assertThat(context.missingCapabilities()).extracting(MissingCapability::target)
                .doesNotContain("apm_business_id");
    }

    @Test
    @DisplayName("动态缺口：APM 锚点且无 LTS 标识 → LTS 关联缺口")
    void dynamicMissingForApmWithoutLts() {
        when(apmDiscovery.searchApplication(any())).thenReturn(new ApmSearchApplicationResponse(
                List.of(app("env-a", 1L, "svc-a", 100L)), 1, null));
        when(apmDiscovery.getEnvMonitorItems(any(), any()))
                .thenReturn(new ApmEnvMonitorItemsResponse(List.of(), List.of()));

        ResourceContext context = service.discover(request(111092L, 1L, null, null));

        assertThat(context.missingCapabilities()).extracting(MissingCapability::target)
                .contains("lts_log_group_id");
        assertThat(context.missingCapabilities()).extracting(MissingCapability::target)
                .doesNotContain("node_name");
    }

    @Test
    @DisplayName("provenance：USER_PROVIDED 与上游 sourceApi 分离")
    void provenanceSeparated() {
        when(apmDiscovery.searchApplication(any())).thenReturn(new ApmSearchApplicationResponse(
                List.of(app("env-a", 1L, "svc-a", 100L)), 1, null));
        when(apmDiscovery.getEnvMonitorItems(any(), any()))
                .thenReturn(new ApmEnvMonitorItemsResponse(List.of(), List.of()));

        ResourceContext context = service.discover(request(111092L, 1L, null, "cn-north-9"));

        ResourceIdentifier region = context.identifiers().stream()
                .filter(id -> id.name().equals("region")).findFirst().orElseThrow();
        assertThat(region.sourceTool()).isEqualTo("USER_PROVIDED");
        assertThat(region.sourceApi()).isEqualTo("USER_PROVIDED");

        ResourceIdentifier appId = context.identifiers().stream()
                .filter(id -> id.name().equals("apm_app_name")).findFirst().orElseThrow();
        assertThat(appId.sourceTool()).isEqualTo("search_apm_application");
        assertThat(appId.sourceApi()).isEqualTo("ApmSearchApplication");
    }

    @Test
    @DisplayName("APM alarm 锚点原样保留可供趋势链使用")
    void apmAlarmAnchorsRetained() {
        when(apmDiscovery.searchApplication(any())).thenReturn(new ApmSearchApplicationResponse(
                List.of(app("env-a", 2297428L, "svc-a", 100L)), 1, null));
        when(apmDiscovery.getEnvMonitorItems(any(), any()))
                .thenReturn(new ApmEnvMonitorItemsResponse(List.of(), List.of(item(121982L))));

        ResourceContext context = service.discover(new DiscoveryRequest("cn-north-9", null, "svc-a", null, null,
                null, null, 111092L, 2297428L, 2141002L, 121982L, "10.0.0.42", "16557997", null, null, null, null, null));

        assertThat(context.identifiers()).extracting(ResourceIdentifier::name)
                .contains("region", "apm_app_name", "apm_business_id", "apm_env_id", "apm_instance_id",
                        "apm_monitor_item_id", "ip_address", "alarm_id");
    }

    @Test
    @DisplayName("分页：目标在第 2 页也能收敛")
    void targetOnSecondPageFound() {
        when(apmDiscovery.searchApplication(any())).thenAnswer(invocation -> {
            ApmSearchApplicationRequest req = invocation.getArgument(0);
            if (req.page() == 1) {
                return new ApmSearchApplicationResponse(page(1, 100), 101, null);
            }
            return new ApmSearchApplicationResponse(List.of(app("target-env", 200L, "target", 900L)), 101, null);
        });
        when(apmDiscovery.getEnvMonitorItems(any(), any()))
                .thenReturn(new ApmEnvMonitorItemsResponse(List.of(), List.of()));

        ResourceContext context = service.discover(request(111092L, 200L, null, null));

        assertThat(context.identifiers()).anyMatch(id ->
                id.name().equals("apm_app_name") && id.value().equals("target")
                        && "ApmSearchApplication".equals(id.sourceApi()));
        assertThat(context.candidates()).isEmpty();
    }

    @Test
    @DisplayName("分页：无匹配返回 missing")
    void noMatchAcrossPages() {
        when(apmDiscovery.searchApplication(any()))
                .thenReturn(new ApmSearchApplicationResponse(List.of(), 0, null));

        ResourceContext context = service.discover(request(111092L, null, null, null));

        assertThat(context.missingCapabilities()).extracting(MissingCapability::target)
                .contains("apm_app_name");
    }

    @Test
    @DisplayName("分页：多页多个匹配返回全部候选")
    void multiPageMultipleMatches() {
        List<ApmAppInfo> page1 = new ArrayList<>();
        page1.add(app("env-a", 1L, "svc-a", 100L));
        for (int i = 1; i < 100; i++) {
            page1.add(app("env-" + i, (long) i, "other-" + i, (long) i));
        }
        when(apmDiscovery.searchApplication(any())).thenAnswer(invocation -> {
            ApmSearchApplicationRequest req = invocation.getArgument(0);
            if (req.page() == 1) {
                return new ApmSearchApplicationResponse(page1, 101, null);
            }
            return new ApmSearchApplicationResponse(
                    List.of(app("env-b", 101L, "svc-a", 101L)), 101, null);
        });

        ResourceContext context = service.discover(new DiscoveryRequest(null, null, "svc-a", null, null,
                null, null, 111092L, null, null, null, null, null, null, null, null, null, null));

        assertThat(context.candidates()).hasSize(2);
        assertThat(context.candidates()).allMatch(candidate ->
                candidate.matchType() == MatchType.NAME_MATCH);
    }

    @Test
    @DisplayName("分页：元数据异常（total 为 null 且短页）安全终止")
    void paginationMetadataAnomalyStops() {
        when(apmDiscovery.searchApplication(any())).thenAnswer(invocation -> {
            ApmSearchApplicationRequest req = invocation.getArgument(0);
            if (req.page() == 1) {
                return new ApmSearchApplicationResponse(page(1, 100), null, null);
            }
            return new ApmSearchApplicationResponse(page(101, 30), null, null);
        });

        ResourceContext context = service.discover(request(111092L, null, null, null));

        verify(apmDiscovery, times(2)).searchApplication(any());
        assertThat(context.candidates()).hasSize(130);
    }

    @Test
    @DisplayName("分页：安全上限，total 恒为 null 且每页满页时最多 MAX_PAGES 页")
    void paginationSafetyCapBound() {
        when(apmDiscovery.searchApplication(any()))
                .thenReturn(new ApmSearchApplicationResponse(page(1, 100), null, null));

        service.discover(request(111092L, null, null, null));

        verify(apmDiscovery, times(20)).searchApplication(any());
    }

    @Test
    @DisplayName("分页：第一页 total 偏小且满 100 条时第二页目标仍被发现")
    void firstPageTotalUnderreportedStillFindsTarget() {
        when(apmDiscovery.searchApplication(any())).thenAnswer(invocation -> {
            ApmSearchApplicationRequest req = invocation.getArgument(0);
            if (req.page() == 1) {
                return new ApmSearchApplicationResponse(page(1, 100), 100, null);
            }
            return new ApmSearchApplicationResponse(
                    List.of(app("target-env", 200L, "target", 900L)), 100, null);
        });
        when(apmDiscovery.getEnvMonitorItems(any(), any()))
                .thenReturn(new ApmEnvMonitorItemsResponse(List.of(), List.of()));

        ResourceContext context = service.discover(request(111092L, 200L, null, null));

        assertThat(context.identifiers()).anyMatch(id ->
                id.name().equals("apm_app_name") && id.value().equals("target")
                        && "ApmSearchApplication".equals(id.sourceApi()));
        assertThat(context.candidates()).isEmpty();
    }

    @Test
    @DisplayName("收敛：envId 同值去重为单一规范标识")
    void envIdSameValueDeduped() {
        when(apmDiscovery.searchApplication(any())).thenReturn(new ApmSearchApplicationResponse(
                List.of(app("env-b", 2L, "svc-b", 101L)), 1, null));
        when(apmDiscovery.getEnvMonitorItems(any(), any()))
                .thenReturn(new ApmEnvMonitorItemsResponse(List.of(), List.of()));

        ResourceContext context = service.discover(request(111092L, 2L, null, null));

        assertThat(context.identifiers()).filteredOn(id -> id.name().equals("apm_env_id"))
                .hasSize(1)
                .allMatch(id -> id.value().equals("2"));
    }

    @Test
    @DisplayName("收敛：appName 同值去重为单一规范标识")
    void appNameSameValueDeduped() {
        when(apmDiscovery.searchApplication(any())).thenReturn(new ApmSearchApplicationResponse(
                List.of(app("env-a", 1L, "svc-a", 100L)), 1, null));

        ResourceContext context = service.discover(new DiscoveryRequest(null, null, "svc-a", null, null,
                null, null, 111092L, null, null, null, null, null, null, null, null, null, null));

        assertThat(context.identifiers()).filteredOn(id -> id.name().equals("apm_app_name"))
                .hasSize(1)
                .allMatch(id -> id.value().equals("svc-a"));
    }

    @Test
    @DisplayName("收敛：monitorItemId 同值去重为单一规范标识")
    void monitorItemSameValueDeduped() {
        when(apmDiscovery.searchApplication(any())).thenReturn(new ApmSearchApplicationResponse(
                List.of(app("env-a", 1L, "svc-a", 100L)), 1, null));
        when(apmDiscovery.getEnvMonitorItems(any(), any()))
                .thenReturn(new ApmEnvMonitorItemsResponse(List.of(), List.of(item(121982L))));

        ResourceContext context = service.discover(new DiscoveryRequest(null, null, null, null, null,
                null, null, 111092L, 1L, null, 121982L, null, null, null, null, null, null, null));

        assertThat(context.identifiers()).filteredOn(id -> id.name().equals("apm_monitor_item_id"))
                .hasSize(1)
                .allMatch(id -> id.value().equals("121982"));
    }

    @Test
    @DisplayName("收敛：LTS 日志组 id 异值冲突保留 provenance")
    void ltsGroupIdConflictPreservesProvenance() {
        when(ltsDiscovery.listLogStreams(any(), any())).thenReturn(new LtsListLogStreamsResponse(
                List.of(stream("gid-upstream", "sid-1"))));

        ResourceContext context = service.discover(
                ltsRequest("gid-user", "grp", null, null));

        assertThat(context.identifiers()).filteredOn(id -> id.name().equals("lts_log_group_id"))
                .hasSize(2)
                .allMatch(ResourceIdentifier::ambiguous)
                .extracting(ResourceIdentifier::value)
                .containsExactlyInAnyOrder("gid-user", "gid-upstream");
        assertThat(context.missingCapabilities()).extracting(MissingCapability::target)
                .contains("lts_log_group_id");
    }

    @Test
    @DisplayName("收敛：LTS 日志流 id 异值冲突保留 provenance")
    void ltsStreamIdConflictPreservesProvenance() {
        when(ltsDiscovery.listLogStreams(any(), any())).thenReturn(new LtsListLogStreamsResponse(
                List.of(stream("gid-1", "sid-upstream"))));

        ResourceContext context = service.discover(ltsRequest(null, "grp", "sid-user", null));

        assertThat(context.identifiers()).filteredOn(id -> id.name().equals("lts_log_stream_id"))
                .hasSize(2)
                .allMatch(ResourceIdentifier::ambiguous)
                .extracting(ResourceIdentifier::value)
                .containsExactlyInAnyOrder("sid-user", "sid-upstream");
        assertThat(context.missingCapabilities()).extracting(MissingCapability::target)
                .contains("lts_log_stream_id");
    }

    @Test
    @DisplayName("收敛：LTS 日志流 id 同值去重为单一规范标识")
    void ltsStreamSameValueDeduped() {
        when(ltsDiscovery.listLogStreams(any(), any())).thenReturn(new LtsListLogStreamsResponse(
                List.of(stream("gid-1", "sid-1"))));

        ResourceContext context = service.discover(ltsRequest(null, "grp", "sid-1", null));

        assertThat(context.identifiers()).filteredOn(id -> id.name().equals("lts_log_stream_id"))
                .hasSize(1)
                .allMatch(id -> id.value().equals("sid-1"));
    }

    private DiscoveryRequest request(Long businessId, Long envId, Long instanceId, String region) {
        return new DiscoveryRequest(region, null, null, null, null, null, null, businessId, envId, instanceId,
                null, null, null, null, null, null, null, null);
    }

    private DiscoveryRequest ltsRequest(String logGroupId, String logGroupName, String logStreamId,
            String logStreamName) {
        return new DiscoveryRequest(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, logGroupId, logGroupName, logStreamId, logStreamName);
    }

    private ApmAppInfo app(String envName, Long envId, String appName, Long appId) {
        return new ApmAppInfo(envName, envId, appName, appId, 1, 0, 0);
    }

    private ApmMonitorItemEntity item(long monitorItemId) {
        return new ApmMonitorItemEntity(null, "jvm", "jvm", null, monitorItemId, null, 18, null, null);
    }

    private LtsLogStream stream(String logGroupId, String logStreamId) {
        return new LtsLogStream(null, logStreamId, null, null, Map.of(), null, null, null, null, null, null,
                logGroupId, null);
    }

    private List<ApmAppInfo> page(int firstEnv, int count) {
        List<ApmAppInfo> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long env = (long) firstEnv + i;
            list.add(app("env-" + env, env, "svc-" + env, env));
        }
        return list;
    }
}
