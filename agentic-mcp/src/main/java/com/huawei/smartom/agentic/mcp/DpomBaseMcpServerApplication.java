/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved
 */

package com.huawei.smartom.agentic.mcp;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for DPOMBaseMCPServer.
 *
 * <p>The component scan base is set to the project root package so beans declared in
 * {@code agentic-common} / {@code agentic-adapter-*} / {@code agentic-monitoring} are picked up.
 *
 * @author h00884391
 * @since 2026-05-21
 */
@SpringBootApplication(scanBasePackages = "com.huawei.smartom.agentic")
@EnableConfigurationProperties(HuaweiCloudProperties.class)
public class DpomBaseMcpServerApplication {

    /**
     * Spring Boot entry point that bootstraps the DPOMBaseMCPServer application context.
     *
     * @param args JVM command-line arguments forwarded to {@link SpringApplication#run}
     */
    public static void main(String[] args) {
        SpringApplication.run(DpomBaseMcpServerApplication.class, args);
    }
}
