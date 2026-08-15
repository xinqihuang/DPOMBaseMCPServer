/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs.dto;

/**
 * OBS 证据包对象内容（get 结果）。
 *
 * @param objectKey 对象键
 * @param content   对象字节内容
 * @param etag      对象 ETag
 *
 * @author h00884391
 * @since 2026-08-15
 */
public record ObsObjectContent(String objectKey, byte[] content, String etag) {
}
