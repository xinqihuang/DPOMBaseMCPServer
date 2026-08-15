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
 *
 * @author h00884391
 * @since 2026-08-15
 */
public record ObsObjectMetadata(String objectKey, long contentLength, String etag) {
}
