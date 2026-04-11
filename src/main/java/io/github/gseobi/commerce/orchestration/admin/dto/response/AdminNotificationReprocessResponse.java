package io.github.gseobi.commerce.orchestration.admin.dto.response;

import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminView;
import java.time.LocalDateTime;

public record AdminNotificationReprocessResponse(
        Long notificationEventId,
        Long orderId,
        String status,
        String handlingPolicy,
        int retryCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime lastAttemptAt,
        String failureCode,
        String failureReason,
        String orderStatus,
        String action
) {

    public static AdminNotificationReprocessResponse from(
            NotificationAdminView view,
            String orderStatus,
            String action
    ) {
        return new AdminNotificationReprocessResponse(
                view.notificationEventId(),
                view.orderId(),
                view.status(),
                view.handlingPolicy(),
                view.retryCount(),
                view.nextAttemptAt(),
                view.lastAttemptAt(),
                view.failureCode(),
                view.failureReason(),
                orderStatus,
                action
        );
    }
}
