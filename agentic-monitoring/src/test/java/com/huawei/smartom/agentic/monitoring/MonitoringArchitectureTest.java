/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Phase 1B 证据与调查边界的架构回归测试。 */
class MonitoringArchitectureTest {

    @Test
    void monitoringDoesNotReceiveProviderTypesCredentialsOrPersistenceClients() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.huawei.smartom.agentic.monitoring");

        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "com.huaweicloud..", "com.obs.services..",
                "java.sql..", "javax.sql..", "org.mybatis..", "org.apache.kafka..",
                "com.dpom.sre..", "com.dpom.agent..", "io.deepeval..")
                .check(classes);
        noClasses().should().dependOnClassesThat().haveSimpleName("HuaweiCloudProperties")
                .check(classes);
    }
}
