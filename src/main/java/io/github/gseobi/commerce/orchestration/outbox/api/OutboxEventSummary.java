package io.github.gseobi.commerce.orchestration.outbox.api;

import java.time.LocalDateTime;

public record OutboxEventSummary(
        Long outboxEventId,
        String topic,
        String eventType,
        String status,
        String payload,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        String failureReason
) {
}
