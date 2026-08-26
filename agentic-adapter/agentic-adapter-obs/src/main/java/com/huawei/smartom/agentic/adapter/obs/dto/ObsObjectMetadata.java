/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs.dto;

/**
 * OBS 证据包对象元数据（head 结果）。
 *
 * @param objectKey     对象键
 * @param contentLength 对象字节数
 * @param etag          对象 ETag
 * @param sha256        对象 SHA-256 用户元数据
 *
 * @author h00884391
 * @since 2026-08-15
 */
public record ObsObjectMetadata(String objectKey, long contentLength, String etag, String sha256) {

    /**
     * 兼容不需要 SHA-256 元数据的调用方。
     *
     * @param objectKey 对象键
     * @param contentLength 对象字节数
     * @param etag 对象 ETag
     */
    public ObsObjectMetadata(String objectKey, long contentLength, String etag) {
        this(objectKey, contentLength, etag, null);
    }
}
