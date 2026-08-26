/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.evidence;

import com.huawei.smartom.agentic.adapter.obs.ObsEvidenceAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 自动 OBS 证据存储装配与失败关闭测试。
 *
 * @author Codex
 * @since 2026-08-26
 */
class ObsEvidenceStorageConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ObsEvidenceStorageConfiguration.class)
            .withBean(ObsEvidenceAdapter.class, () -> mock(ObsEvidenceAdapter.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void automaticStorageIsDisabledByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(BoundedEvidenceArtifactStore.class));
    }

    @Test
    void incompleteEnabledConfigurationFailsClosed() {
        runner.withPropertyValues("dpom.obs.automatic-storage-enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void completeEnvironmentConfigurationCreatesStore() {
        runner.withPropertyValues(
                "dpom.obs.automatic-storage-enabled=true",
                "dpom.obs.enabled=true",
                "dpom.obs.endpoint=https://obs.example.invalid",
                "dpom.obs.bucket=runtime-bucket",
                "dpom.obs.prefix=runtime-prefix",
                "dpom.obs.service-code=dpom-base")
                .run(context -> assertThat(context).hasSingleBean(BoundedEvidenceArtifactStore.class));
    }
}
