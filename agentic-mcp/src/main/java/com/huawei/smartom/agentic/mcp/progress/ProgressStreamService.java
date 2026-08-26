/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.progress;

import com.huawei.smartom.agentic.diagnosis.model.ProgressWindow;
import com.huawei.smartom.agentic.diagnosis.port.ProgressPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 以有界虚拟线程读取持久化日志的可恢复 SSE 服务。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Service
@ConditionalOnProperty(prefix = "dpom.investigation.progress-api", name = "enabled", havingValue = "true")
public class ProgressStreamService {
    private final ProgressPort progress;
    private final ProgressApiProperties properties;
    private final Semaphore clients;

    /**
     * 创建 SSE 服务。
     *
     * @param progress 权威进度端口
     * @param properties 容量配置
     */
    public ProgressStreamService(ProgressPort progress, ProgressApiProperties properties) {
        this.progress = progress;
        this.properties = properties;
        clients = new Semaphore(properties.maxClients());
    }

    /**
     * 在有容量时打开连接。
     *
     * @param investigationId 调查身份
     * @param afterSequence 客户端最后已接收序号
     * @return SSE emitter；无容量时为空
     */
    public SseEmitter open(String investigationId, long afterSequence) {
        if (!clients.tryAcquire()) {
            return null;
        }
        SseEmitter emitter = new SseEmitter(properties.connectionDuration().toMillis());
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> releaseOnce(released);
        emitter.onCompletion(release);
        emitter.onTimeout(release);
        emitter.onError(ignored -> release.run());
        Thread.startVirtualThread(() -> stream(investigationId, afterSequence, emitter, released));
        return emitter;
    }

    private void stream(String investigationId, long afterSequence, SseEmitter emitter, AtomicBoolean released) {
        long cursor = afterSequence;
        long deadline = System.nanoTime() + properties.connectionDuration().toNanos();
        long heartbeatAt = System.nanoTime();
        try {
            while (System.nanoTime() < deadline) {
                ProgressWindow window = progress.window(investigationId, cursor, properties.bufferLimit());
                if (window.requiresResynchronization(cursor)) {
                    emitter.send(SseEmitter.event().name("resynchronize").data(
                            new ProgressApiError("RETENTION_GAP", true)));
                    break;
                }
                for (var record : window.records()) {
                    emitter.send(SseEmitter.event().id(Long.toString(record.progressSequence()))
                            .name("progress").data(ProgressResponse.from(record)));
                    cursor = record.progressSequence();
                }
                heartbeatAt = heartbeat(emitter, heartbeatAt);
                Thread.sleep(properties.pollInterval());
            }
            emitter.complete();
        }
        catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            emitter.complete();
        }
        finally {
            releaseOnce(released);
        }
    }

    private long heartbeat(SseEmitter emitter, long previous) throws IOException {
        if (System.nanoTime() - previous >= properties.heartbeatInterval().toNanos()) {
            emitter.send(SseEmitter.event().name("heartbeat").data(Instant.now().toString()));
            return System.nanoTime();
        }
        return previous;
    }

    private void releaseOnce(AtomicBoolean released) {
        if (released.compareAndSet(false, true)) {
            clients.release();
        }
    }
}
