/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.approval;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;
import com.huawei.smartom.agentic.monitoring.approval.ApprovalProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 审批控制面 nonce 防重放缓存（有界 + 持久化 + fail-closed）。
 *
 * <p>nonce 格式/长度受限；达到容量时 fail-closed 拒绝；接受即落盘（失败回滚并抛稳定错误）；重启加载窗口内 nonce，
 * 拒绝重启窗口内重放。日志不含绝对路径、异常 message/stack 或密钥。
 *
 * @author h00884391
 * @since 2026-08-16
 */
@Component
@ConditionalOnProperty(name = "dpom.approval.enabled", havingValue = "true")
public class ApprovalNonceCache {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalNonceCache.class);

    private static final String NONCE_PATTERN = "[a-zA-Z0-9_-]{16,128}";

    private final ConcurrentHashMap<String, Long> nonces = new ConcurrentHashMap<>();
    private final ApprovalProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path storePath;

    /**
     * 构造 nonce 缓存并从磁盘加载窗口内 nonce。
     *
     * @param properties 审批配置（nonce-cache-size / nonce-store-file）
     */
    public ApprovalNonceCache(ApprovalProperties properties) {
        this.properties = properties;
        this.storePath = Path.of(properties.getNonceStoreFile()).toAbsolutePath();
        load();
    }

    /**
     * 原子记录一个 nonce；格式非法、已存在未过期或容量已满均拒绝。
     *
     * @param nonce            客户端 nonce
     * @param validUntilMillis nonce 有效截止时间（epoch 毫秒）
     * @return true 表示接受，false 表示重放/非法/容量已满
     */
    public boolean tryRecord(String nonce, long validUntilMillis) {
        if (!isValidNonce(nonce)) {
            return false;
        }
        long now = System.currentTimeMillis();
        AtomicBoolean accepted = new AtomicBoolean(false);
        nonces.compute(nonce, (key, existing) -> {
            if (existing == null || existing <= now) {
                accepted.set(true);
                return validUntilMillis;
            }
            return existing;
        });
        if (!accepted.get()) {
            return false;
        }
        evictExpired();
        if (nonces.size() > properties.getNonceCacheSize()) {
            nonces.remove(nonce);
            return false;
        }
        try {
            persist();
        }
        catch (SmartomException exception) {
            nonces.remove(nonce);
            throw exception;
        }
        return true;
    }

    private boolean isValidNonce(String nonce) {
        return nonce != null && nonce.matches(NONCE_PATTERN);
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        nonces.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private synchronized void persist() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, "dpom-nonces", ".tmp");
            try {
                mapper.writeValue(temp.toFile(), nonces);
                Files.move(temp, storePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            finally {
                Files.deleteIfExists(temp);
            }
        }
        catch (IOException exception) {
            throw new SmartomException(ErrorCode.APPROVAL_STORAGE_ERROR, "nonce store unavailable");
        }
    }

    private void load() {
        if (!Files.exists(storePath)) {
            return;
        }
        try {
            if (Files.size(storePath) == 0) {
                return;
            }
            JavaType mapType = mapper.getTypeFactory().constructMapType(Map.class, String.class, Long.class);
            Map<String, Long> loaded = mapper.readValue(storePath.toFile(), mapType);
            nonces.putAll(loaded);
            evictExpired();
        }
        catch (IOException exception) {
            LOG.error("nonce store file is unreadable");
            throw new IllegalStateException("nonce store file is unreadable");
        }
    }
}
