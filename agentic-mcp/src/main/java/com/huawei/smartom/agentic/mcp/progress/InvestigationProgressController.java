/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import com.huawei.smartom.agentic.diagnosis.port.InvestigationRepository;
import com.huawei.smartom.agentic.diagnosis.port.ProgressPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Portal 使用的只读权威 Investigation REST 与 SSE 接口。
 *
 * @author Codex
 * @since 2026-08-25
 */
@RestController
@RequestMapping("/api/v1/investigations")
@ConditionalOnProperty(prefix = "dpom.investigation.progress-api", name = "enabled", havingValue = "true")
public class InvestigationProgressController {
    private final InvestigationRepository investigations;
    private final ProgressPort progress;
    private final ProgressStreamService streams;
    private final ProgressApiProperties properties;
    private final PortalAuthorization authorization;

    /**
     * 创建只读控制器。
     *
     * @param investigations 调查仓储
     * @param progress 进度日志
     * @param streams SSE 服务
     * @param properties 容量配置
     * @param authorization 授权校验器
     */
    public InvestigationProgressController(InvestigationRepository investigations, ProgressPort progress,
                                           ProgressStreamService streams, ProgressApiProperties properties,
                                           PortalAuthorization authorization) {
        this.investigations = investigations;
        this.progress = progress;
        this.streams = streams;
        this.properties = properties;
        this.authorization = authorization;
    }

    /**
     * 查询权威快照。
     *
     * @param investigationId 调查身份
     * @param auth 授权头
     * @return 快照或稳定错误
     */
    @GetMapping("/{investigationId}")
    public ResponseEntity<?> snapshot(@PathVariable String investigationId,
                                      @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (!authorization.permits(auth)) {
            return unauthorized();
        }
        return investigations.find(investigationId)
                .<ResponseEntity<?>>map(value -> ResponseEntity.ok(InvestigationSnapshotResponse.from(value)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ProgressApiError("INVESTIGATION_NOT_FOUND", false)));
    }

    /**
     * 分页查询权威进度历史。
     *
     * @param investigationId 调查身份
     * @param after 排他游标
     * @param limit 请求条数
     * @param auth 授权头
     * @return 有界进度页或稳定错误
     */
    @GetMapping("/{investigationId}/progress")
    public ResponseEntity<?> history(@PathVariable String investigationId,
                                     @RequestParam(defaultValue = "0") long after,
                                     @RequestParam(defaultValue = "50") int limit,
                                     @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (!authorization.permits(auth)) {
            return unauthorized();
        }
        if (after < 0L || limit < 1) {
            return ResponseEntity.badRequest().body(new ProgressApiError("INVALID_CURSOR", false));
        }
        var window = progress.window(investigationId, after, Math.min(limit, properties.pageLimit()));
        if (window.requiresResynchronization(after)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ProgressApiError("RETENTION_GAP", true));
        }
        var records = window.records().stream().map(ProgressResponse::from).toList();
        long next = records.isEmpty() ? after : records.getLast().sequence();
        return ResponseEntity.ok(new ProgressPageResponse(window.oldestSequence(), window.latestSequence(),
                next, records));
    }

    /**
     * 打开支持 Last-Event-ID 的可恢复 SSE。
     *
     * @param investigationId 调查身份
     * @param lastEventId 客户端最后已接收序号
     * @param auth 授权头
     * @return emitter 或稳定错误
     * @throws ProgressApiException 鉴权、游标、保留窗口或容量门禁失败
     */
    @GetMapping(value = "/{investigationId}/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(
            @PathVariable String investigationId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (!authorization.permits(auth)) {
            throw new ProgressApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", false);
        }
        Optional<Long> after = parseCursor(lastEventId);
        if (after.isEmpty()) {
            throw new ProgressApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", false);
        }
        var window = progress.window(investigationId, after.orElseThrow(), 1);
        if (window.requiresResynchronization(after.orElseThrow())) {
            throw new ProgressApiException(HttpStatus.CONFLICT, "RETENTION_GAP", true);
        }
        var emitter = streams.open(investigationId, after.orElseThrow());
        if (emitter == null) {
            throw new ProgressApiException(HttpStatus.TOO_MANY_REQUESTS, "SSE_CAPACITY_EXHAUSTED", false);
        }
        return emitter;
    }

    private Optional<Long> parseCursor(String value) {
        try {
            long parsed = value == null || value.isBlank() ? 0L : Long.parseLong(value);
            return parsed < 0L ? Optional.empty() : Optional.of(parsed);
        }
        catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private ResponseEntity<ProgressApiError> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ProgressApiError("UNAUTHORIZED", false));
    }
}
