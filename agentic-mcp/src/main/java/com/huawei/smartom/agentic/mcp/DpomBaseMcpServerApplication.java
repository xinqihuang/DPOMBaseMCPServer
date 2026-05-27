/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.mcp;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * DPOMBaseMCPServer 启动入口。
 *
 * <p>组件扫描根包设为项目顶层包，确保 {@code agentic-common} / {@code agentic-adapter-*} /
 * {@code agentic-monitoring} 中声明的 Bean 均能被正确加载。
 *
 * @author h00884391
 * @since 2026-05-21
 */
@SpringBootApplication(scanBasePackages = "com.huawei.smartom.agentic")
@EnableConfigurationProperties(HuaweiCloudProperties.class)
public class DpomBaseMcpServerApplication {

    /**
     * Spring Boot 启动入口，负责初始化 DPOMBaseMCPServer 应用上下文。
     *
     * @param args JVM 命令行参数，将原样转发给 {@link SpringApplication#run}
     */
    public static void main(String[] args) {
        SpringApplication.run(DpomBaseMcpServerApplication.class, args);
    }
}
