/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.persistence;

import com.huawei.smartom.agentic.diagnosis.model.AuditRecord;
import com.huawei.smartom.agentic.diagnosis.model.AuthorityEpoch;
import com.huawei.smartom.agentic.diagnosis.model.Conclusion;
import com.huawei.smartom.agentic.diagnosis.model.ConclusionType;
import com.huawei.smartom.agentic.diagnosis.model.CommandReceipt;
import com.huawei.smartom.agentic.diagnosis.model.ExternalCallRecord;
import com.huawei.smartom.agentic.diagnosis.model.ExternalCallState;
import com.huawei.smartom.agentic.diagnosis.model.FrozenPublication;
import com.huawei.smartom.agentic.diagnosis.model.Hypothesis;
import com.huawei.smartom.agentic.diagnosis.model.HypothesisStatus;
import com.huawei.smartom.agentic.diagnosis.model.Investigation;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationBudget;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationCheckpoint;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationRun;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStatus;
import com.huawei.smartom.agentic.diagnosis.model.InvestigationStep;
import com.huawei.smartom.agentic.diagnosis.model.Observation;
import com.huawei.smartom.agentic.diagnosis.model.ProgressRecord;
import com.huawei.smartom.agentic.diagnosis.model.ProgressStatus;
import com.huawei.smartom.agentic.diagnosis.model.PublicationIntentRequest;
import com.huawei.smartom.agentic.diagnosis.port.TerminalCommit;

import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Investigation MyBatis 持久化与事务特征测试。
 *
 * @author Codex
 * @since 2026-08-25
 */
class InvestigationPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private MyBatisInvestigationPersistenceAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/deployment/phase1b/001_investigation_forward.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        transactions = new TransactionTemplate(manager);
        adapter = adapter(sqlSessionFactory());
    }

    @Test
    void createsReadsUpdatesAndRecoversAcrossSessionFactoryRestart() throws Exception {
        Investigation initial = investigation(InvestigationStatus.RUNNING, 1);
        assertThat(Boolean.TRUE.equals(transactions.execute(status -> adapter.insert(initial)))).isTrue();
        assertThat(adapter.find("inv-1")).contains(initial);

        Investigation paused = investigation(InvestigationStatus.PAUSED, 2);
        assertThat(Boolean.TRUE.equals(transactions.execute(status -> adapter.update(paused, 1)))).isTrue();
        assertThat(Boolean.TRUE.equals(transactions.execute(status -> adapter.update(paused, 1)))).isFalse();

        MyBatisInvestigationPersistenceAdapter restarted = adapter(sqlSessionFactory());
        assertThat(restarted.find("inv-1")).contains(paused);
    }

    @Test
    void atomicallyCommitsEligibleTerminalFactsAndRejectsRacingWriter() {
        transactions.executeWithoutResult(status -> adapter.insert(investigation(InvestigationStatus.RUNNING, 1)));
        TerminalCommit commit = completedCommit("audit-1", "intent-1", "event-1", 1);

        assertThat(Boolean.TRUE.equals(transactions.execute(status -> adapter.commitTerminal(commit)))).isTrue();
        assertThat(Boolean.TRUE.equals(transactions.execute(status -> adapter.commitTerminal(
                completedCommit("audit-2", "intent-2", "event-2", 2))))).isFalse();
        assertThat(count("investigation_conclusion")).isEqualTo(1);
        assertThat(count("investigation_progress")).isEqualTo(1);
        assertThat(count("investigation_audit")).isEqualTo(1);
        assertThat(count("diagnosis_publication_intent")).isEqualTo(1);
    }

    @Test
    void rollsBackAllTerminalFactsWhenOuterTransactionFails() {
        transactions.executeWithoutResult(status -> adapter.insert(investigation(InvestigationStatus.RUNNING, 1)));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            assertThat(adapter.commitTerminal(completedCommit("audit-1", "intent-1", "event-1", 1))).isTrue();
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(adapter.find("inv-1").orElseThrow().status()).isEqualTo(InvestigationStatus.RUNNING);
        assertThat(count("investigation_conclusion")).isZero();
        assertThat(count("investigation_progress")).isZero();
        assertThat(count("investigation_audit")).isZero();
        assertThat(count("diagnosis_publication_intent")).isZero();
    }

    @Test
    void enforcesProgressAuditAndPublicationUniquenessWithoutReplacement() {
        transactions.executeWithoutResult(status -> adapter.insert(investigation(InvestigationStatus.RUNNING, 1)));
        ProgressRecord progress = progress(1);
        adapter.append(progress);
        adapter.append(new AuditRecord("audit-1", "inv-1", "START", "RUNNING", 1, NOW));
        assertThat(adapter.after("inv-1", 0, 500)).containsExactly(progress);
        assertThat(adapter.window("inv-1", 0, 500).oldestSequence()).isEqualTo(1);
        assertThat(adapter.window("inv-1", 0, 500).latestSequence()).isEqualTo(1);

        assertThatThrownBy(() -> adapter.append(progress)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> adapter.append(
                new AuditRecord("audit-1", "inv-1", "START", "FAILED", 1, NOW)))
                .isInstanceOf(RuntimeException.class);
        assertThat(adapter.append(intent("intent-1", "event-1", 1))).isTrue();
        assertThatThrownBy(() -> adapter.append(intent("intent-2", "event-1", 2)))
                .isInstanceOf(RuntimeException.class);
        assertThat(count("investigation_audit")).isEqualTo(1);
    }

    @Test
    void persistsEveryRecoveryAndIdempotencyFactThroughTypedXmlMappers() throws Exception {
        transactions.executeWithoutResult(status -> adapter.insert(investigation(InvestigationStatus.RUNNING, 1)));
        InvestigationDetailMapper details = new SqlSessionTemplate(sqlSessionFactory())
                .getMapper(InvestigationDetailMapper.class);
        transactions.executeWithoutResult(status -> {
            assertThat(details.insertRun(new InvestigationRun("run-1", "inv-1", 1,
                    InvestigationStatus.RUNNING, 1, NOW, null))).isEqualTo(1);
            assertThat(details.insertStep("inv-1", new InvestigationStep("step-1", "run-1", 1,
                    "COLLECT", "SUCCEEDED", NOW))).isEqualTo(1);
            assertThat(details.insertObservation(new Observation("observation-1", "inv-1",
                    "evidence-1", "OBSERVED", NOW))).isEqualTo(1);
            Hypothesis hypothesis = new Hypothesis("hypothesis-1", "inv-1", "ROOT_CAUSE",
                    HypothesisStatus.SUPPORTED, List.of("evidence-1"), NOW);
            assertThat(details.insertHypothesis(HypothesisRow.from(hypothesis))).isEqualTo(1);
            assertThat(details.insertCheckpoint(new InvestigationCheckpoint("checkpoint-1", "inv-1",
                    "run-1", 1, 2, ExternalCallState.PLANNED, NOW))).isEqualTo(1);
            assertThat(details.insertCommandReceipt(new CommandReceipt("command-1", "inv-1",
                    "a".repeat(64), "ACCEPTED", NOW))).isEqualTo(1);
            assertThat(details.insertExternalCall(new ExternalCallRecord("call-1", "inv-1", "call-key-1",
                    ExternalCallState.PLANNED, 0, null, NOW))).isEqualTo(1);
        });
        assertThat(count("investigation_run")).isEqualTo(1);
        assertThat(count("investigation_step")).isEqualTo(1);
        assertThat(count("investigation_checkpoint")).isEqualTo(1);
        assertThat(count("investigation_command_receipt")).isEqualTo(1);
        assertThat(count("investigation_external_call")).isEqualTo(1);
    }

    @Test
    void readinessFailsClosedForMissingWrongOrRollbackRequestedSchema() {
        Integer requiredTables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = CURRENT_SCHEMA() AND table_name IN ("
                + "'investigation', 'investigation_budget', 'investigation_run', 'investigation_step', "
                + "'investigation_observation', 'investigation_hypothesis', 'investigation_conclusion', "
                + "'investigation_checkpoint', 'investigation_progress', 'diagnosis_publication_intent', "
                + "'investigation_audit', 'investigation_command_receipt', 'investigation_external_call')",
                Integer.class);
        assertThat(requiredTables).isEqualTo(13);
        assertThatCode(() -> new InvestigationSchemaReadiness(jdbc, 1).afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new InvestigationSchemaReadiness(jdbc, 2).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("investigation schema readiness failed");
        jdbc.update("UPDATE dpom_schema_state SET compatibility_state = 'ROLLBACK_REQUESTED'");
        assertThatThrownBy(() -> new InvestigationSchemaReadiness(jdbc, 1).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("investigation schema readiness failed");
    }

    @Test
    void invalidEnabledConfigurationFailsWithoutCredentialDisclosure() {
        InvestigationPersistenceProperties properties = new InvestigationPersistenceProperties(true,
                "jdbc:mysql://secret-host:3306/private", "", "credential-value", "com.mysql.cj.jdbc.Driver", 1);
        assertThatThrownBy(() -> new InvestigationPersistenceConfiguration().investigationDataSource(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid investigation persistence configuration")
                .hasMessageNotContaining("secret-host")
                .hasMessageNotContaining("credential-value");
    }

    @Test
    void fencesLeasesRecoversExpiryAndReplaysFrozenContentWithAudit() {
        transactions.executeWithoutResult(status -> adapter.insert(investigation(InvestigationStatus.RUNNING, 1)));
        assertThat(adapter.append(intent("intent-1", "event-1", 1))).isTrue();
        FrozenPublication frozen = new FrozenPublication("intent-1", "event-1", "inv-1",
                "dpom.diagnosis-event.v2", 1, 2, "epoch-1", "{}".getBytes(), "a".repeat(64), NOW);
        assertThat(adapter.freeze(frozen)).isTrue();
        assertThat(adapter.freeze(frozen)).isFalse();

        var first = adapter.leaseEligible("worker-1", NOW, 10, Duration.ofSeconds(30),
                3, Duration.ofDays(1)).getFirst();
        assertThat(adapter.acknowledge("intent-1", "stale-token", NOW)).isFalse();
        assertThat(adapter.recordFailure("intent-1", first.fencingToken(), NOW.plusSeconds(5),
                false, "TRANSIENT_DELIVERY_FAILURE")).isTrue();
        assertThat(adapter.leaseEligible("worker-2", NOW.plusSeconds(4), 10,
                Duration.ofSeconds(30), 3, Duration.ofDays(1))).isEmpty();
        var second = adapter.leaseEligible("worker-2", NOW.plusSeconds(5), 10,
                Duration.ofSeconds(30), 3, Duration.ofDays(1)).getFirst();
        assertThat(second.fencingToken()).isNotEqualTo(first.fencingToken());
        assertThat(second.publication().canonicalBytes()).isEqualTo(frozen.canonicalBytes());
        assertThat(adapter.acknowledge("intent-1", second.fencingToken(), NOW.plusSeconds(6))).isTrue();
        assertThat(adapter.pendingCount()).isZero();

        assertThat(adapter.requestReplay("intent-1", "operator-7", "BROKER_RECOVERED",
                NOW.plusSeconds(7))).isTrue();
        assertThat(adapter.pendingCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT operator_ref FROM investigation_audit "
                + "WHERE action_code = 'PUBLICATION_REPLAY'", String.class)).isEqualTo("operator-7");
        assertThat(jdbc.queryForObject("SELECT canonical_sha256 FROM diagnosis_publication_intent "
                + "WHERE intent_id = 'intent-1'", String.class)).isEqualTo(frozen.canonicalSha256());
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mappers/investigation/*.xml"));
        return factory.getObject();
    }

    private MyBatisInvestigationPersistenceAdapter adapter(SqlSessionFactory factory) {
        InvestigationMapper mapper = new SqlSessionTemplate(factory).getMapper(InvestigationMapper.class);
        return new MyBatisInvestigationPersistenceAdapter(mapper);
    }

    private Investigation investigation(InvestigationStatus status, long version) {
        InvestigationBudget budget = new InvestigationBudget(10, 20, 1000, 300, 0, 0, 0, 0);
        AuthorityEpoch authority = new AuthorityEpoch("DPOMBaseMCPServer", "epoch-1", NOW);
        return new Investigation("inv-1", "incident-1", status, version, budget, authority, "run-1", NOW);
    }

    private TerminalCommit completedCommit(String auditId, String intentId, String eventId, long sequence) {
        Investigation completed = investigation(InvestigationStatus.COMPLETED, 2);
        Conclusion conclusion = new Conclusion("conclusion-1", "inv-1", ConclusionType.ROOT_CAUSE_IDENTIFIED,
                "ROOT_CAUSE", List.of("evidence-1"), NOW);
        return new TerminalCommit(completed, 1, conclusion, progress(sequence),
                new AuditRecord(auditId, "inv-1", "TERMINALIZE", "COMPLETED", 2, NOW),
                intent(intentId, eventId, sequence));
    }

    private ProgressRecord progress(long sequence) {
        return new ProgressRecord("progress-" + sequence, "inv-1", "run-1", sequence, 2,
                ProgressStatus.COMPLETED, "TERMINALIZATION", "COMPLETED", NOW);
    }

    private PublicationIntentRequest intent(String intentId, String eventId, long sequence) {
        return new PublicationIntentRequest(intentId, eventId, "inv-1", "run-1", 2, sequence,
                new AuthorityEpoch("DPOMBaseMCPServer", "epoch-1", NOW), NOW);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

}
