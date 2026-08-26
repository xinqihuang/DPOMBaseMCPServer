/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.smartom.agentic.diagnosis.port.DiagnosticReportRepository.ReportAudit;
import com.huawei.smartom.agentic.diagnosis.report.PublishedDiagnosticReport;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

/** Phase 5 diagnosis-only 报告 MyBatis 契约测试。 */
class DiagnosticReportPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T01:00:00Z");
    private MyBatisDiagnosticReportRepository repository;
    private TransactionTemplate transactions;

    @BeforeEach void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/deployment/phase1b/003_diagnostic_report_forward.sql")).execute(dataSource);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mappers/investigation/*.xml"));
        SqlSessionFactory sessions = factory.getObject();
        repository = new MyBatisDiagnosticReportRepository(
                new SqlSessionTemplate(sessions).getMapper(DiagnosticReportMapper.class));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test void insertsEnforcesUniquenessPagesAndReconstructsAfterAdapterRestart() throws Exception {
        transactions.executeWithoutResult(status -> repository.publish(report("REPORT-1", "REQ-1", 1, null, 'a'),
                audit("REPORT-1", "AUD-1")));
        transactions.executeWithoutResult(status -> repository.publish(report("REPORT-2", "REQ-2", 2,
                "REPORT-1", 'b'), audit("REPORT-2", "AUD-2")));
        assertThat(repository.page("INV-1", null, 1)).extracting(PublishedDiagnosticReport::revisionNumber)
                .containsExactly(2L);
        assertThat(repository.page("INV-1", 2L, 10)).extracting(PublishedDiagnosticReport::reportId)
                .containsExactly("REPORT-1");
        assertThat(repository.findByRequest("REQ-2")).get().extracting(PublishedDiagnosticReport::reportId)
                .isEqualTo("REPORT-2");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> repository.publish(
                report("REPORT-3", "REQ-2", 3, "REPORT-2", 'c'), audit("REPORT-3", "AUD-3"))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void reportAndAuditAreOneTransaction() {
        transactions.executeWithoutResult(status -> repository.publish(report("REPORT-T0", "REQ-T0", 1, null, 'd'),
                audit("REPORT-T0", "AUD-T0")));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> repository.publish(
                report("REPORT-T1", "REQ-T1", 2, "REPORT-T0", 'e'), audit("REPORT-T1", "AUD-T0"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.find("REPORT-T1")).isEmpty();
    }

    private PublishedDiagnosticReport report(String id, String request, long revision,
                                               String supersedes, char digest) {
        return new PublishedDiagnosticReport(id, request, String.valueOf(digest).repeat(64), "INC-1", "INV-1",
                "RUN-1", revision, supersedes, revision == 1 ? null : "LIFECYCLE_UPDATED", "a".repeat(64),
                "{}", Character.toString((char) (digest + 5)).repeat(64), "COMPLETE", "operator", NOW);
    }

    private ReportAudit audit(String report, String audit) {
        return new ReportAudit(audit, report, "PUBLISHED", "operator", "GENERATED", NOW);
    }
}
