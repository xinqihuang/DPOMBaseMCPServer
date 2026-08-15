/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.approval;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.smartom.agentic.common.error.ErrorCode;
import com.huawei.smartom.agentic.common.exception.SmartomException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于文件的审批存储（单实例，fail-closed）。
 *
 * <p>以进程内 {@link ConcurrentHashMap} 为真相源，每次变更全量序列化为 JSON 写临时文件后 {@code ATOMIC_MOVE}；
 * 持久化失败时回滚内存并抛稳定错误（不 fail-open）；启动时文件损坏/不可读则 fail-closed 拒绝启动。日志不含绝对路径、
 * 异常 message/stack 或密钥。
 *
 * @author h00884391
 * @since 2026-08-16
 */
@Component
public class PersistentApprovalStore implements ApprovalStore {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentApprovalStore.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ApprovalRecord> approvals = new ConcurrentHashMap<>();
    private final Path storePath;

    /**
     * 构造存储并从磁盘加载已有审批。
     *
     * @param properties 审批配置（store-file）
     */
    public PersistentApprovalStore(ApprovalProperties properties) {
        this.storePath = Path.of(properties.getStoreFile()).toAbsolutePath();
        load();
    }

    @Override
    public void approve(ApprovalRecord record) {
        String key = keyOf(record);
        ApprovalRecord previous = approvals.put(key, record);
        try {
            persist();
        }
        catch (RuntimeException exception) {
            rollbackApprove(key, record, previous);
            throw exception;
        }
    }

    @Override
    public boolean revoke(String serviceCode, String investigationId, String packageId, String sha256) {
        String key = keyOf(serviceCode, investigationId, packageId, sha256);
        ApprovalRecord removed = approvals.remove(key);
        if (removed == null) {
            return false;
        }
        try {
            persist();
        }
        catch (RuntimeException exception) {
            approvals.put(key, removed);
            throw exception;
        }
        return true;
    }

    @Override
    public ApprovalRecord consume(String serviceCode, String investigationId, String packageId, String sha256) {
        String key = keyOf(serviceCode, investigationId, packageId, sha256);
        ApprovalRecord record = approvals.remove(key);
        if (record == null) {
            return null;
        }
        if (record.expiresAtMillis() <= System.currentTimeMillis()) {
            cleanupExpired();
            return null;
        }
        try {
            persist();
        }
        catch (RuntimeException exception) {
            approvals.put(key, record);
            throw exception;
        }
        return record;
    }

    @Override
    public void restore(ApprovalRecord record) {
        String key = keyOf(record);
        ApprovalRecord previous = approvals.putIfAbsent(key, record);
        if (previous != null) {
            return;
        }
        try {
            persist();
        }
        catch (RuntimeException exception) {
            approvals.remove(key, record);
            throw exception;
        }
    }

    @Override
    public boolean isApproved(String serviceCode, String investigationId, String packageId, String sha256) {
        String key = keyOf(serviceCode, investigationId, packageId, sha256);
        ApprovalRecord record = approvals.get(key);
        if (record == null) {
            return false;
        }
        if (record.expiresAtMillis() <= System.currentTimeMillis()) {
            approvals.remove(key);
            cleanupExpired();
            return false;
        }
        return true;
    }

    private void rollbackApprove(String key, ApprovalRecord record, ApprovalRecord previous) {
        if (previous == null) {
            approvals.remove(key, record);
        }
        else {
            approvals.put(key, previous);
        }
    }

    private void cleanupExpired() {
        try {
            persist();
        }
        catch (SmartomException exception) {
            LOG.error("approval storage persistence failed");
        }
    }

    private String keyOf(ApprovalRecord record) {
        return keyOf(record.serviceCode(), record.investigationId(), record.packageId(), record.sha256());
    }

    private String keyOf(String serviceCode, String investigationId, String packageId, String sha256) {
        return serviceCode + "|" + investigationId + "|" + packageId + "|" + sha256;
    }

    private synchronized void persist() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, "dpom-approvals", ".tmp");
            try {
                mapper.writeValue(temp.toFile(), approvals);
                Files.move(temp, storePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            finally {
                Files.deleteIfExists(temp);
            }
        }
        catch (IOException exception) {
            throw new SmartomException(ErrorCode.APPROVAL_STORAGE_ERROR, "approval storage unavailable");
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
            JavaType mapType = mapper.getTypeFactory().constructMapType(Map.class, String.class, ApprovalRecord.class);
            Map<String, ApprovalRecord> loaded = mapper.readValue(storePath.toFile(), mapType);
            approvals.putAll(loaded);
        }
        catch (IOException exception) {
            LOG.error("approval store file is unreadable");
            throw new IllegalStateException("approval store file is unreadable");
        }
    }
}
