/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.adapter.apm.transport;

import com.huaweicloud.sdk.core.http.HttpRequest;
import com.huaweicloud.sdk.core.http.HttpResponse;

/**
 * APM 告警规则管理接口的认证 HTTP 传输边界。
 *
 * @author h00884391
 * @since 2026-08-26
 */
@FunctionalInterface
public interface ApmAlarmRuleAdminTransport {

    /**
     * 对请求应用华为云认证并同步发送。
     *
     * @param request 未认证的 HTTP 请求
     * @return 华为云 HTTP 响应
     */
    HttpResponse execute(HttpRequest request);
}
