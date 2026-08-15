/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.approval;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.huawei.smartom.agentic.common.exception.InvalidParamException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 审批审计日志不泄密/注入测试：审计日志含身份/事件但不含 secret/signature/body，且未验证身份被净化。
 *
 * @author h00884391
 * @since 2026-08-16
 */
class ApprovalAuditLogLeakTest {

    @Test
    @DisplayName("审计日志不记录正文/原因/密钥")
    void auditLogDoesNotLeakBodyOrSecret() throws IOException {
        Logger logger = (Logger) LoggerFactory.getLogger(ApprovalService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ApprovalService service = newService();
            String secretMarker = "TOP-SECRET-REASON-BODY";

            service.approve("svc", "inv", "pkg", "a".repeat(64), "approver", secretMarker);

            List<String> messages = messagesOf(appender);
            assertThat(messages).anyMatch(message -> message.contains("APPROVE"));
            assertThat(messages).anyMatch(message -> message.contains("svc"));
            assertThat(messages).noneMatch(message -> message.contains(secretMarker));
            assertThat(messages).noneMatch(message -> message.contains("signature"));
        }
        finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("未验证身份在审计中被净化（日志注入防护）")
    void invalidIdentitySanitizedInAudit() throws IOException {
        Logger logger = (Logger) LoggerFactory.getLogger(ApprovalService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ApprovalService service = newService();
            String injection = "bad\nserviceCode";

            Throwable throwable = catchThrowable(() ->
                    service.approve(injection, "inv", "pkg", "a".repeat(64), "approver", "reason"));

            assertThat(throwable).isInstanceOf(InvalidParamException.class);
            List<String> messages = messagesOf(appender);
            assertThat(messages).noneMatch(message -> message.contains("bad\nserviceCode"));
            assertThat(messages).anyMatch(message -> message.contains("INVALID"));
        }
        finally {
            logger.detachAppender(appender);
        }
    }

    private ApprovalService newService() throws IOException {
        ApprovalProperties properties = new ApprovalProperties();
        Path storeFile = Files.createTempFile("dpom-approvals", ".json");
        properties.setStoreFile(storeFile.toString());
        return new ApprovalService(new PersistentApprovalStore(properties), properties, new SimpleMeterRegistry());
    }

    private List<String> messagesOf(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
