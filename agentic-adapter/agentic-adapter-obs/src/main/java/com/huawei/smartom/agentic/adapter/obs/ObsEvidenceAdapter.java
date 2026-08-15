/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.obs;

import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectContent;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsObjectMetadata;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceRequest;
import com.huawei.smartom.agentic.adapter.obs.dto.ObsPutEvidenceResponse;
import com.huawei.smartom.agentic.common.exception.SmartomException;

/**
 * 华为云 OBS 证据包转移适配器端口。
 *
 * <p>仅暴露 Diagnostic Evidence Package 所需的 put / head / get 三个操作，不提供 list / delete /
 * copy 等通用 OBS 管理能力。bucket / prefix / region / endpoint / 凭据均由服务端配置，不来自调用方。
 *
 * @author h00884391
 * @since 2026-08-15
 */
public interface ObsEvidenceAdapter {

    /**
     * 上传证据包到 OBS（服务端加密）。
     *
     * @param request 上传请求（objectKey / content / sha256），不可为 null
     * @return 上传结果（objectKey / etag / size）
     * @throws SmartomException 携带对应 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    ObsPutEvidenceResponse putEvidence(ObsPutEvidenceRequest request);

    /**
     * 获取证据包对象元数据（head）。
     *
     * @param objectKey 服务端生成的对象键，不可为 null
     * @return 对象元数据
     * @throws SmartomException 携带对应 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    ObsObjectMetadata headEvidence(String objectKey);

    /**
     * 获取证据包对象内容（get）。
     *
     * @param objectKey 服务端生成的对象键，不可为 null
     * @return 对象内容
     * @throws SmartomException 携带对应 {@link com.huawei.smartom.agentic.common.error.ErrorCode}
     */
    ObsObjectContent getEvidence(String objectKey);
}
