package io.github.gseobi.commerce.orchestration.admin.api;

import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminNotificationReprocessResponse;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminOutboxReprocessResponse;

public interface AdminReprocessingFacade {

    AdminNotificationReprocessResponse retryNotification(Long notificationEventId);

    AdminNotificationReprocessResponse retryNotification(
            Long notificationEventId,
            AdminRecoveryContext recoveryContext
    );

    AdminNotificationReprocessResponse ignoreNotification(Long notificationEventId);

    AdminNotificationReprocessResponse ignoreNotification(
            Long notificationEventId,
            AdminRecoveryContext recoveryContext
    );

    AdminOutboxReprocessResponse retryOutboxDeadLetter(Long outboxEventId);

    AdminOutboxReprocessResponse retryOutboxDeadLetter(
            Long outboxEventId,
            AdminRecoveryContext recoveryContext
    );
}
