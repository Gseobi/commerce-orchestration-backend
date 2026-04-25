package io.github.gseobi.commerce.orchestration.admin.dto.response;

import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminView;
import java.time.LocalDateTime;

public record AdminNotificationReprocessResponse(
        Long eventId,
        Long orderId,
        String action,
        String result,
        String previousStatus,
        String currentStatus,
        String message,
        String handlingPolicy,
        int retryCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime lastAttemptAt,
        String failureCode,
        String failureReason,
        String orderStatus
) {

    public static AdminNotificationReprocessResponse from(
            NotificationAdminView view,
            String orderStatus,
            String action,
            String result,
            String message
    ) {
        return new AdminNotificationReprocessResponse(
                view.notificationEventId(),
                view.orderId(),
                action,
                result,
                view.previousStatus(),
                view.status(),
                message,
                view.handlingPolicy(),
                view.retryCount(),
                view.nextAttemptAt(),
                view.lastAttemptAt(),
                view.failureCode(),
                view.failureReason(),
                orderStatus
        );
    }
}
