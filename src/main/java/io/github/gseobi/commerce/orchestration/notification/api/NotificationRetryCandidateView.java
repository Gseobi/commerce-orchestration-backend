package io.github.gseobi.commerce.orchestration.notification.api;

import java.time.LocalDateTime;

public record NotificationRetryCandidateView(
        Long notificationEventId,
        Long orderId,
        int retryCount,
        LocalDateTime nextAttemptAt
) {
}
