/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.ProgressStatus;
import com.huawei.smartom.agentic.diagnosis.model.ProgressWindow;
import com.huawei.smartom.agentic.diagnosis.port.InvestigationRepository;
import com.huawei.smartom.agentic.diagnosis.port.ProgressPort;
import com.huawei.smartom.agentic.messaging.ProgressV1Builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 权威调查查询、鉴权、保留缺口与跨传输进度契约测试。
 *
 * @author Codex
 * @since 2026-08-25
 */
class InvestigationProgressControllerTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private InvestigationRepository investigations;
    private ProgressPort progress;
    private ProgressStreamService streams;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        investigations = mock(InvestigationRepository.class);
        progress = mock(ProgressPort.class);
        streams = mock(ProgressStreamService.class);
        var properties = properties();
        var controller = new InvestigationProgressController(investigations, progress, streams,
                properties, new PortalAuthorization(properties));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ProgressApiExceptionHandler()).build();
    }

    @Test
    void authenticatesAndReturnsOnlySafeBoundedSnapshotAndHistory() throws Exception {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        var authority = new AuthorityEpoch("DPOMBaseMCPServer", "epoch-2", now);
        var budget = new InvestigationBudget(10, 10, 1000, 60, 1, 1, 10, 1);
        when(investigations.find("INV-1")).thenReturn(Optional.of(new Investigation("INV-1", "INC-1",
                InvestigationStatus.RUNNING, 3, budget, authority, "RUN-1", now)));
        ProgressRecord record = progress(now);
        when(progress.window(eq("INV-1"), anyLong(), anyInt()))
                .thenReturn(new ProgressWindow(1, 1, List.of(record)));

        String snapshot = mvc.perform(get("/api/v1/investigations/INV-1")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(snapshot).doesNotContain("budget", "evidence", "prompt", "model", "token");
        mvc.perform(get("/api/v1/investigations/INV-1/progress").param("limit", "10000")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records.length()").value(1));
    }

    @Test
    void rejectsUnauthorizedMutationAndRetentionGap() throws Exception {
        mvc.perform(get("/api/v1/investigations/INV-1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/investigations/INV-1").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isMethodNotAllowed());
        when(progress.window("INV-1", 3, 50)).thenReturn(new ProgressWindow(7, 9, List.of()));
        mvc.perform(get("/api/v1/investigations/INV-1/progress").param("after", "3")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.resynchronize").value(true));
    }

    @Test
    void resumesSseAndKafkaUsesTheSamePersistedSequenceAndState() throws Exception {
        when(progress.window("INV-1", 8, 1)).thenReturn(new ProgressWindow(1, 9, List.of()));
        when(streams.open("INV-1", 8)).thenReturn(new SseEmitter());
        mvc.perform(get("/api/v1/investigations/INV-1/progress/stream")
                        .header("Authorization", "Bearer " + TOKEN).header("Last-Event-ID", "8"))
                .andExpect(status().isOk());
        verify(streams).open("INV-1", 8);

        ProgressRecord record = progress(Instant.parse("2026-08-25T00:00:00Z"));
        byte[] bytes = new ProgressV1Builder(new ObjectMapper()).build(record,
                new AuthorityEpoch("DPOMBaseMCPServer", "epoch-2", record.occurredAt()), null, null)
                .canonicalBytes();
        String kafka = new String(bytes, StandardCharsets.UTF_8);
        ProgressResponse rest = ProgressResponse.from(record);
        assertThat(kafka).contains("\"progressSequence\":" + rest.sequence(),
                "\"status\":\"" + rest.status() + "\"");
    }

    @Test
    void rejectsSseWhenBoundedClientCapacityIsExhausted() throws Exception {
        when(progress.window("INV-1", 0, 1)).thenReturn(new ProgressWindow(0, 0, List.of()));
        when(streams.open("INV-1", 0)).thenReturn(null);

        mvc.perform(get("/api/v1/investigations/INV-1/progress/stream")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("SSE_CAPACITY_EXHAUSTED"));
    }

    private ProgressApiProperties properties() {
        return new ProgressApiProperties(true, TOKEN, 100, 2, 25, Duration.ofMinutes(5),
                Duration.ofSeconds(15), Duration.ofMillis(100));
    }

    private ProgressRecord progress(Instant now) {
        return new ProgressRecord("PROG-1", "INV-1", "RUN-1", 9, 3,
                ProgressStatus.RUNNING, "EVIDENCE", "STEP_COMPLETED", now);
    }
}
