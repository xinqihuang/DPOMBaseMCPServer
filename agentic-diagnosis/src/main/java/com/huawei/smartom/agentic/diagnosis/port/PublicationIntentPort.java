/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.diagnosis.port;

import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;

/**
  * 不可变 publication intent 的服务本地端口。
  * @author Codex
  * @since 2026-08-25
  */
public interface PublicationIntentPort {
    /**
     * 追加不可变 publication intent。
     *
     * @param request 发布意图
     * @return 首次追加成功返回 true
     */
    boolean append(PublicationIntentRequest request);
}
