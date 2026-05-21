package com.huawei.smartom.agentic.common.health;

import com.huawei.smartom.agentic.common.config.HuaweiCloudProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class HuaweiCloudCredentialsHealthIndicatorTest {

    @Test
    @DisplayName("All fields populated -> UP")
    void allFieldsUp() {
        HuaweiCloudCredentialsHealthIndicator h = build("cn-southwest-2", "ak", "sk", "proj-1");
        Health health = h.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("region", "cn-southwest-2");
    }

    @Test
    @DisplayName("AK null -> DOWN")
    void akNullDown() {
        HuaweiCloudCredentialsHealthIndicator h = build("cn-southwest-2", null, "sk", "proj-1");
        Health health = h.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "HUAWEICLOUD_AK is missing");
    }

    @Test
    @DisplayName("SK blank -> DOWN")
    void skBlankDown() {
        HuaweiCloudCredentialsHealthIndicator h = build("cn-southwest-2", "ak", "   ", "proj-1");
        Health health = h.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "HUAWEICLOUD_SK is missing");
    }

    @Test
    @DisplayName("Region missing -> DOWN")
    void regionMissingDown() {
        HuaweiCloudCredentialsHealthIndicator h = build("", "ak", "sk", "proj-1");
        Health health = h.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "huaweicloud.region is missing");
    }

    @Test
    @DisplayName("ProjectId missing -> DOWN")
    void projectIdMissingDown() {
        HuaweiCloudCredentialsHealthIndicator h = build("cn-southwest-2", "ak", "sk", null);
        Health health = h.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "HUAWEICLOUD_PROJECT_ID is missing");
    }

    private HuaweiCloudCredentialsHealthIndicator build(
            String region, String ak, String sk, String projectId) {
        HuaweiCloudProperties p = new HuaweiCloudProperties();
        p.setRegion(region);
        p.setAk(ak);
        p.setSk(sk);
        p.setProjectId(projectId);
        return new HuaweiCloudCredentialsHealthIndicator(p);
    }
}
