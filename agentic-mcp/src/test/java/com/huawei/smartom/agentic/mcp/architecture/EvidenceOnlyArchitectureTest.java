/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** DPOMBase 仅保留证据采集、发现与受控 Artifact 操作的架构护栏。 */
class EvidenceOnlyArchitectureTest {

    private static final Pattern TOOL_NAME = Pattern.compile(
            "@Tool\\s*\\(.*?name\\s*=\\s*\\\"([^\\\"]+)\\\"", Pattern.DOTALL);

    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "batch_query_ces_metric_data", "correlate_incident", "discover_resource_context",
            "get_evidence_package", "get_service_topology", "head_evidence_package",
            "list_alarm_notify", "list_alarms", "list_aom_events", "list_aom_metrics",
            "list_apm_alarm_data", "list_apm_business", "list_ces_metrics", "list_lts_log_groups",
            "list_lts_log_streams", "list_notification_masks", "put_evidence_package",
            "query_aom_metric_data", "query_ces_metric_data", "query_logs", "query_lts_log_context",
            "query_lts_logs", "query_traces", "resolve_resource_candidates", "search_apm_application",
            "show_apm_monitor_item_view_config", "show_apm_trend", "show_clob_detail",
            "show_env_monitor_items", "show_event_detail", "show_trace_events");

    @Test
    void diagnosisStateMessagingAndExternalContractsAreAbsent() throws IOException {
        Path root = repositoryRoot();
        assertRemovedModule(root.resolve("agentic-diagnosis"));
        assertRemovedModule(root.resolve("agentic-persistence"));
        assertRemovedModule(root.resolve("agentic-messaging"));

        for (Path file : activeBuildFiles(root)) {
            assertThat(Files.readString(file))
                    .as("active build file %s", root.relativize(file))
                    .doesNotContain("com.huawei.smartom.agentic.diagnosis")
                    .doesNotContain("com.huawei.smartom.agentic.messaging")
                    .doesNotContain("org.apache.kafka")
                    .doesNotContain("org.mybatis")
                    .doesNotContain("com.baomidou")
                    .doesNotContain("langchain4j")
                    .doesNotContain("deepseek")
                    .doesNotContain("openai")
                    .doesNotContain("DiagnosisEventV2Builder")
                    .doesNotContain("InvestigationProgressController")
                    .doesNotContain("DiagnosisReportApplicationService")
                    .doesNotContain("DiagnosticReport")
                    .doesNotContain("InvestigationRepository")
                    .doesNotContain("DiagnoseTraceService")
                    .doesNotContain("ApmAlarmRuleAdminAdapter")
                    .doesNotContain("UpdateAlarmRuleStatus")
                    .doesNotContain("CreateNotificationMask")
                    .doesNotContain("DeleteNotificationMask")
                    .doesNotContain("../contracts")
                    .doesNotContain("contracts/conformance");
        }
    }

    @Test
    void toolCatalogIsAnExplicitEvidenceOnlyAllowList() throws IOException {
        Path toolDirectory = repositoryRoot().resolve(
                "agentic-mcp/src/main/java/com/huawei/smartom/agentic/mcp/tool");
        Set<String> actual = new HashSet<>();
        try (Stream<Path> files = Files.list(toolDirectory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = TOOL_NAME.matcher(Files.readString(file));
                while (matcher.find()) {
                    actual.add(matcher.group(1));
                }
            }
        }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(ALLOWED_TOOLS);
        assertThat(actual).noneMatch(name -> name.matches(
                ".*(diagnos|conclud|report|publish|disable|enable|mask_create|mask_delete|mutat|change).*"));
    }

    private static Set<Path> activeBuildFiles(Path root) throws IOException {
        Set<Path> result = new HashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("target"))
                    .filter(path -> path.getFileName().toString().equals("pom.xml")
                            || path.toString().contains("src\\main")
                            || path.toString().contains("src/main"))
                    .forEach(result::add);
        }
        return result;
    }

    private static void assertRemovedModule(Path module) {
        assertThat(module.resolve("pom.xml")).doesNotExist();
        assertThat(module.resolve("src")).doesNotExist();
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("agentic-mcp/pom.xml"))) {
            candidate = candidate.getParent();
        }
        assertThat(candidate).as("DPOMBaseMCPServer repository root").isNotNull();
        return candidate;
    }
}
