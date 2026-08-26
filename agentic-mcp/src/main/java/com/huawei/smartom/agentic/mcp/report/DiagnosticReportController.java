/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.huawei.smartom.agentic.diagnosis.report.PublishedDiagnosticReport;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * diagnosis-only 权威报告的有界创建、查询和重放 API。
 *
 * @author Codex
 * @since 2026-08-26
 */
@RestController
@RequestMapping("/api/v5/diagnostic-reports")
@ConditionalOnBean(DiagnosisReportApplicationService.class)
public class DiagnosticReportController {
    private final DiagnosisReportApplicationService service;
    private final DiagnosticReportAuthorization authorization;
    /**
     * 创建控制器。
     *
     * @param service 报告服务
     * @param authorization 认证器
     */
    public DiagnosticReportController(DiagnosisReportApplicationService service,
            DiagnosticReportAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    /**
     * 创建一个基于持久化事实的报告修订。
     *
     * @param request 请求
     * @param token 认证 token
     * @return 已发布报告
     */
    @PostMapping
    public ResponseEntity<PublishedDiagnosticReport> generate(@RequestBody GenerateRequest request,
            @RequestHeader(value = "X-DPOM-Report-Token", required = false) String token) {
        authorization.require(token);
        return ResponseEntity.status(201).body(service.generate(new DiagnosisReportApplicationService.GenerateCommand(
                request.requestId(), request.investigationId(), request.expectedLatestRevision(),
                request.changeReason(), "portal-operator")));
    }

    /**
     * 精确读取权威 JSON。
     *
     * @param reportId 报告身份
     * @param token 认证 token
     * @return 报告 JSON
     */
    @GetMapping("/{reportId}")
    public JsonNode exact(@PathVariable String reportId,
            @RequestHeader(value = "X-DPOM-Report-Token", required = false) String token) {
        authorization.require(token);
        return service.exact(reportId);
    }

    /**
     * 从冻结 JSON 重放。
     *
     * @param reportId 报告身份
     * @param token 认证 token
     * @return 报告 JSON
     */
    @PostMapping("/{reportId}/replays")
    public JsonNode replay(@PathVariable String reportId,
            @RequestHeader(value = "X-DPOM-Report-Token", required = false) String token) {
        authorization.require(token);
        return service.replay(reportId);
    }

    /**
     * 有界读取调查修订历史。
     *
     * @param investigationId 调查身份
     * @param beforeRevision 修订游标
     * @param limit 数量上限
     * @param token 认证 token
     * @return 报告页
     */
    @GetMapping("/investigations/{investigationId}")
    public List<PublishedDiagnosticReport> history(@PathVariable String investigationId,
            @RequestParam(required = false) Long beforeRevision, @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-DPOM-Report-Token", required = false) String token) {
        authorization.require(token);
        return service.page(investigationId, beforeRevision, limit);
    }

    /**
     * 报告生成请求。
     *
     * @param requestId 请求身份
     * @param investigationId 调查身份
     * @param expectedLatestRevision 预期最新修订
     * @param changeReason 变更原因
     * @author Codex
     * @since 2026-08-26
     */
    public record GenerateRequest(String requestId, String investigationId, long expectedLatestRevision,
                                  String changeReason) { }
}
