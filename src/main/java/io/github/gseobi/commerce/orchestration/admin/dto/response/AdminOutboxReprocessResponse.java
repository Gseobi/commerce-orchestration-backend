package io.github.gseobi.commerce.orchestration.admin.dto.response;

import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminView;
import java.time.LocalDateTime;

public record AdminOutboxReprocessResponse(
        Long eventId,
        Long aggregateId,
        String eventType,
        String action,
        String result,
        String previousStatus,
        String currentStatus,
        int retryCount,
        String failureCode,
        String failureReason,
        String message,
        LocalDateTime nextAttemptAt,
        LocalDateTime lastAttemptAt,
        LocalDateTime publishedAt,
        LocalDateTime deadLetteredAt
) {

    public static AdminOutboxReprocessResponse from(
            OutboxAdminView view,
            String action,
            String result,
            String message
    ) {
        return new AdminOutboxReprocessResponse(
                view.outboxEventId(),
                view.aggregateId(),
                view.eventType(),
                action,
                result,
                view.previousStatus(),
                view.status(),
                view.retryCount(),
                view.failureCode(),
                view.failureReason(),
                message,
                view.nextAttemptAt(),
                view.lastAttemptAt(),
                view.publishedAt(),
                view.deadLetteredAt()
        );
    }
}
