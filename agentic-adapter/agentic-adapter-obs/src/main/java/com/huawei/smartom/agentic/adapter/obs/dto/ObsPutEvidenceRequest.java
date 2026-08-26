/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs.dto;

/**
 * OBS 证据包上传请求（adapter 内部契约）。
 *
 * @param objectKey 服务端生成的对象键，不可为 null
 * @param content   证据包字节内容，不可为 null
 * @param sha256    证据内容 SHA-256 校验和（十六进制小写），不可为 null
 * @param contentType 受控内容类型，不可为 null
 *
 * @author h00884391
 * @since 2026-08-15
 */
public record ObsPutEvidenceRequest(String objectKey, byte[] content, String sha256, String contentType) {

    /**
     * 使用证据包默认内容类型创建上传请求。
     *
     * @param objectKey 服务端生成的对象键
     * @param content 证据包字节内容
     * @param sha256 证据包 SHA-256
     */
    public ObsPutEvidenceRequest(String objectKey, byte[] content, String sha256) {
        this(objectKey, content, sha256, "application/zip");
    }
}
