/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.monitoring.evidence;

import com.huawei.smartom.agentic.adapter.obs.ObsEvidenceAdapter;
import com.huawei.smartom.agentic.adapter.obs.config.ObsProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自动 OBS 诊断证据存储的默认关闭装配。
 *
 * @author Codex
 * @since 2026-08-26
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObsProperties.class)
@ConditionalOnProperty(prefix = "dpom.obs", name = "automatic-storage-enabled", havingValue = "true")
public class ObsEvidenceStorageConfiguration {

    /**
     * 创建环境配置驱动的 OBS Artifact store。
     *
     * @param adapter OBS SDK 适配器
     * @param properties OBS 环境配置
     * @param mapper JSON 映射器
     * @return 有界证据存储端口
     */
    @Bean
    public BoundedEvidenceArtifactStore obsBoundedEvidenceArtifactStore(
            ObsEvidenceAdapter adapter, ObsProperties properties, ObjectMapper mapper) {
        return new ObsBoundedEvidenceArtifactStore(adapter, properties, mapper);
    }
}
