/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.AuditRecord;
import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.ConclusionType;
import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.ProgressStatus;
import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;
import com.huawei.smartom.agentic.diagnosis.port.TerminalCommit;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 MySQL 8 Investigation 持久化契约。
 *
 * @author Codex
 * @since 2026-08-25
 */
class InvestigationMySqlContractIT {

    @Test
    void verifiesDeploymentSqlTransactionsUniquenessLockingAndRecovery() throws Exception {
        assertThat(System.getProperty("dpom.mysql.contract.required")).isEqualTo("true");
        String url = required("DPOM_MYSQL_URL");
        String username = required("DPOM_MYSQL_USERNAME");
        String password = required("DPOM_MYSQL_PASSWORD");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/deployment/phase1b/001_investigation_forward.sql")).execute(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer deliveryColumns = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'diagnosis_publication_intent' "
                + "AND column_name = 'canonical_sha256'", Integer.class);
        if (deliveryColumns == 0) {
            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/deployment/phase1b/002_publication_delivery_forward.sql")).execute(dataSource);
        }
        new InvestigationSchemaReadiness(jdbc, 1).afterPropertiesSet();
        Integer requiredTables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name IN ("
                + "'investigation', 'investigation_budget', 'investigation_run', 'investigation_step', "
                + "'investigation_observation', 'investigation_hypothesis', 'investigation_conclusion', "
                + "'investigation_checkpoint', 'investigation_progress', 'diagnosis_publication_intent', "
                + "'investigation_audit', 'investigation_command_receipt', 'investigation_external_call')",
                Integer.class);
        assertThat(requiredTables).isEqualTo(13);

        SqlSessionFactory factory = factory(dataSource);
        MyBatisInvestigationPersistenceAdapter adapter = adapter(factory);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String investigationId = "mysql-inv-" + suffix;
        Investigation running = investigation(investigationId, InvestigationStatus.RUNNING, 1);
        assertThat(Boolean.TRUE.equals(transactions.execute(status -> adapter.insert(running)))).isTrue();

        TerminalCommit completed = commit(investigationId, suffix);
        assertThat(Boolean.TRUE.equals(transactions.execute(status -> adapter.commitTerminal(completed)))).isTrue();
        assertThat(Boolean.TRUE.equals(transactions.execute(status -> adapter.commitTerminal(completed)))).isFalse();
        assertThat(adapter.find(investigationId).orElseThrow().status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(adapter(factory(dataSource)).find(investigationId)).isPresent();

        String rollbackId = "mysql-rollback-" + suffix;
        transactions.executeWithoutResult(status -> adapter.insert(
                investigation(rollbackId, InvestigationStatus.RUNNING, 1)));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            adapter.commitTerminal(commit(rollbackId, "rollback-" + suffix));
            throw new IllegalStateException("contract rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(adapter.find(rollbackId).orElseThrow().status()).isEqualTo(InvestigationStatus.RUNNING);

        ProgressRecord progress = completed.progress();
        assertThatThrownBy(() -> adapter.append(progress)).isInstanceOf(RuntimeException.class);

        FrozenPublication frozen = new FrozenPublication(completed.publicationIntent().intentId(),
                completed.publicationIntent().eventId(), investigationId, "dpom.diagnosis-event.v2",
                1, 2, "epoch-1", "{}".getBytes(), "a".repeat(64),
                completed.publicationIntent().createdAt());
        assertThat(adapter.freeze(frozen)).isTrue();
        Instant deliveryTime = completed.publicationIntent().createdAt().plusSeconds(1);
        var lease = adapter.leaseEligible("mysql-worker", deliveryTime, 10,
                Duration.ofSeconds(30), 3, Duration.ofDays(1)).stream()
                .filter(value -> value.publication().intentId().equals(frozen.intentId()))
                .findFirst().orElseThrow();
        assertThat(adapter.acknowledge(frozen.intentId(), "stale", deliveryTime)).isFalse();
        assertThat(adapter.acknowledge(frozen.intentId(), lease.fencingToken(), deliveryTime)).isTrue();
        assertThat(adapter.requestReplay(frozen.intentId(), "mysql-operator", "BROKER_RECOVERED",
                deliveryTime)).isTrue();
        System.out.println("MYSQL_CONTRACT_STATUS=EXECUTED");
    }

    private SqlSessionFactory factory(DriverManagerDataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(configuration);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mappers/investigation/*.xml"));
        return bean.getObject();
    }

    private MyBatisInvestigationPersistenceAdapter adapter(SqlSessionFactory factory) {
        return new MyBatisInvestigationPersistenceAdapter(
                new SqlSessionTemplate(factory).getMapper(InvestigationMapper.class));
    }

    private Investigation investigation(String id, InvestigationStatus status, long version) {
        return new Investigation(id, "incident-" + id, status, version,
                new InvestigationBudget(10, 20, 1000, 300, 0, 0, 0, 0),
                new AuthorityEpoch("DPOMBaseMCPServer", "epoch-1", Instant.parse("2026-08-25T00:00:00Z")),
                "run-" + id, Instant.parse("2026-08-25T00:00:00Z"));
    }

    private TerminalCommit commit(String investigationId, String suffix) {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        Investigation completed = investigation(investigationId, InvestigationStatus.COMPLETED, 2);
        Conclusion conclusion = new Conclusion("conclusion-" + suffix, investigationId,
                ConclusionType.ROOT_CAUSE_IDENTIFIED, "ROOT_CAUSE", List.of("evidence-1"), now);
        ProgressRecord progress = new ProgressRecord("progress-" + suffix, investigationId,
                completed.activeRunId(), 1, 2, ProgressStatus.COMPLETED,
                "TERMINALIZATION", "COMPLETED", now);
        AuditRecord audit = new AuditRecord("audit-" + suffix, investigationId,
                "TERMINALIZE", "COMPLETED", 2, now);
        PublicationIntentRequest intent = new PublicationIntentRequest("intent-" + suffix,
                "event-" + suffix, investigationId, completed.activeRunId(), 2, 1,
                completed.authorityEpoch(), now);
        return new TerminalCommit(completed, 1, conclusion, progress, audit, intent);
    }

    private String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name + " must be set for mysql-contract").isNotBlank();
        return value;
    }
}
