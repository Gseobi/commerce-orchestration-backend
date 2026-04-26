package io.github.gseobi.commerce.orchestration.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommerceRecoveryMetrics {

    private static final String NONE = "none";
    private static final String UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;

    public void incrementOutboxPublishAttempt(String eventType, String topic) {
        increment("commerce.outbox.publish.attempts", "eventType", eventType, "topic", topic);
    }

    public void incrementOutboxPublishSuccess(String eventType, String topic) {
        increment("commerce.outbox.publish.success", "eventType", eventType, "topic", topic);
    }

    public void incrementOutboxPublishFailure(String eventType, String topic, String failureCode) {
        increment("commerce.outbox.publish.failure",
                "eventType", eventType,
                "topic", topic,
                "failureCode", failureCode);
    }

    public void incrementOutboxPublishSkipped(String reason) {
        increment("commerce.outbox.publish.skipped", "reason", reason);
    }

    public void incrementOutboxDeadLetter(String eventType, String topic, String failureCode) {
        increment("commerce.outbox.dead_letter.count",
                "eventType", eventType,
                "topic", topic,
                "failureCode", failureCode);
    }

    public void incrementNotificationRetryBatchStarted() {
        increment("commerce.notification.retry.batch.started");
    }

    public void incrementNotificationRetrySuccess(String failureCode) {
        increment("commerce.notification.retry.success", "failureCode", failureCode);
    }

    public void incrementNotificationRetryFailed(String failureCode) {
        increment("commerce.notification.retry.failure", "failureCode", failureCode);
    }

    public void incrementNotificationRetrySkipped(String reason) {
        increment("commerce.notification.retry.skipped", "reason", reason);
    }

    public void incrementNotificationRetryManualRequired(String failureCode) {
        increment("commerce.notification.retry.manual_required", "failureCode", failureCode);
    }

    public void incrementAdminRecoveryRequest(String target, String action) {
        increment("commerce.admin.recovery.requests", "target", target, "action", action);
    }

    public void incrementAdminRecoverySuccess(String target, String action) {
        increment("commerce.admin.recovery.success", "target", target, "action", action);
    }

    public void incrementAdminRecoveryFailure(String target, String action, String failureCode) {
        increment("commerce.admin.recovery.failure",
                "target", target,
                "action", action,
                "failureCode", failureCode);
    }

    private void increment(String name, String... tags) {
        String[] normalizedTags = new String[tags.length];
        for (int i = 0; i < tags.length; i += 2) {
            normalizedTags[i] = normalizeTagName(tags[i]);
            normalizedTags[i + 1] = normalizeTagValue(tags[i + 1]);
        }
        Counter.builder(name)
                .tags(normalizedTags)
                .register(meterRegistry)
                .increment();
    }

    private String normalizeTagName(String value) {
        return normalize(value, UNKNOWN);
    }

    private String normalizeTagValue(String value) {
        return normalize(value, NONE);
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
