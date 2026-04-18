package io.github.gseobi.commerce.orchestration.notification.api;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRetryOperations {

    List<NotificationRetryCandidateView> findDueRetryScheduledEvents(LocalDateTime now, int limit);

    NotificationAdminView markRetrySucceeded(Long notificationEventId, LocalDateTime attemptedAt);

    NotificationFailureView rescheduleRetry(
            Long notificationEventId,
            String failureCode,
            String failureReason,
            LocalDateTime attemptedAt,
            LocalDateTime nextAttemptAt
    );

    NotificationFailureView requireManualIntervention(
            Long notificationEventId,
            String failureCode,
            String failureReason,
            LocalDateTime attemptedAt
    );
}
