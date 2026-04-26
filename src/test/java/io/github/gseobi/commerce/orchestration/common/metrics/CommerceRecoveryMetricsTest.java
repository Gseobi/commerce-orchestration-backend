package io.github.gseobi.commerce.orchestration.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommerceRecoveryMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private CommerceRecoveryMetrics commerceRecoveryMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        commerceRecoveryMetrics = new CommerceRecoveryMetrics(meterRegistry);
    }

    @Test
    void incrementsOutboxPublishSuccess() {
        commerceRecoveryMetrics.incrementOutboxPublishSuccess("NOTIFICATION", "commerce.notification.requested");

        assertThat(meterRegistry.find("commerce.outbox.publish.success")
                .tag("eventType", "NOTIFICATION")
                .tag("topic", "commerce.notification.requested")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    @Test
    void incrementsOutboxPublishFailureWithFailureCodeTag() {
        commerceRecoveryMetrics.incrementOutboxPublishFailure(
                "NOTIFICATION",
                "commerce.notification.requested",
                "IllegalStateException"
        );

        assertThat(meterRegistry.find("commerce.outbox.publish.failure")
                .tag("eventType", "NOTIFICATION")
                .tag("topic", "commerce.notification.requested")
                .tag("failureCode", "IllegalStateException")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    @Test
    void incrementsNotificationRetrySkipped() {
        commerceRecoveryMetrics.incrementNotificationRetrySkipped("claim_failed");

        assertThat(meterRegistry.find("commerce.notification.retry.skipped")
                .tag("reason", "claim_failed")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    @Test
    void incrementsAdminRecoverySuccessAndFailure() {
        commerceRecoveryMetrics.incrementAdminRecoverySuccess("notification", "RETRY");
        commerceRecoveryMetrics.incrementAdminRecoveryFailure("notification", "RETRY", "BusinessException");

        assertThat(meterRegistry.find("commerce.admin.recovery.success")
                .tag("target", "notification")
                .tag("action", "RETRY")
                .counter()
                .count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.find("commerce.admin.recovery.failure")
                .tag("target", "notification")
                .tag("action", "RETRY")
                .tag("failureCode", "BusinessException")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    @Test
    void normalizesBlankTagValuesWithoutHighCardinalityTags() {
        commerceRecoveryMetrics.incrementOutboxPublishFailure(" ", null, "");

        assertThat(meterRegistry.find("commerce.outbox.publish.failure")
                .tag("eventType", "none")
                .tag("topic", "none")
                .tag("failureCode", "none")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    @Test
    void incrementsAdminRecoveryFailureWithStableTags() {
        commerceRecoveryMetrics.incrementAdminRecoveryFailure("notification", "RETRY", "BusinessException");

        assertThat(meterRegistry.find("commerce.admin.recovery.failure")
                .tag("target", "notification")
                .tag("action", "RETRY")
                .tag("failureCode", "BusinessException")
                .counter()
                .count())
                .isEqualTo(1.0);
    }
}
