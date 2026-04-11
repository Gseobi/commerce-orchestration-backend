package io.github.gseobi.commerce.orchestration.admin.dto.response;

import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminView;
import java.time.LocalDateTime;

public record AdminOutboxReprocessResponse(
        Long outboxEventId,
        Long orderId,
        String status,
        int retryCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime lastAttemptAt,
        LocalDateTime publishedAt,
        LocalDateTime deadLetteredAt,
        String failureCode,
        String failureReason,
        String action
) {

    public static AdminOutboxReprocessResponse from(OutboxAdminView view, String action) {
        return new AdminOutboxReprocessResponse(
                view.outboxEventId(),
                view.orderId(),
                view.status(),
                view.retryCount(),
                view.nextAttemptAt(),
                view.lastAttemptAt(),
                view.publishedAt(),
                view.deadLetteredAt(),
                view.failureCode(),
                view.failureReason(),
                action
        );
    }
}
