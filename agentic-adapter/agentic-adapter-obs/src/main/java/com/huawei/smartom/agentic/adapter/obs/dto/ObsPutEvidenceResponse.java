/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs.dto;

/**
 * OBS 证据包上传结果。
 *
 * @param objectKey 服务端生成的对象键
 * @param etag      OBS 返回的对象 ETag
 * @param size      已上传字节数
 *
 * @author h00884391
 * @since 2026-08-15
 */
public record ObsPutEvidenceResponse(String objectKey, String etag, long size) {
}
