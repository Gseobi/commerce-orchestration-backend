package io.github.gseobi.commerce.orchestration.outbox.api;

import java.time.LocalDateTime;

public record OutboxEventSummary(
        Long outboxEventId,
        String topic,
        String eventType,
        String status,
        String payload,
        int retryCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime createdAt,
        LocalDateTime lastAttemptAt,
        LocalDateTime publishedAt,
        LocalDateTime deadLetteredAt,
        String failureCode,
        String failureReason
) {
}
