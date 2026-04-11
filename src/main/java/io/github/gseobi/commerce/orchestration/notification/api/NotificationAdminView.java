package io.github.gseobi.commerce.orchestration.notification.api;

import java.time.LocalDateTime;

public record NotificationAdminView(
        Long notificationEventId,
        Long orderId,
        String status,
        String handlingPolicy,
        int retryCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime lastAttemptAt,
        String failureCode,
        String failureReason
) {
}
