package com.huawei.smartom.agentic.diagnosis;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class DiagnosisArchitectureTest {

    @Test
    void domainIsFrameworkProviderTransportAndPersistenceNeutral() {
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.huawei.smartom.agentic.diagnosis");

        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "org.mybatis..", "java.sql..", "javax.sql..",
                "org.apache.kafka..", "com.huaweicloud..", "com.obs.services..",
                "com.dpom.sre..", "com.dpom.agent..", "io.deepeval..")
                .check(classes);
    }
}
