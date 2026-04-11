package io.github.gseobi.commerce.orchestration.outbox.api;

import java.time.LocalDateTime;

public record OutboxAdminView(
        Long outboxEventId,
        Long orderId,
        String status,
        int retryCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime lastAttemptAt,
        LocalDateTime publishedAt,
        LocalDateTime deadLetteredAt,
        String failureCode,
        String failureReason
) {
}
