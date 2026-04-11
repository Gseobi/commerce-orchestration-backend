package io.github.gseobi.commerce.orchestration.admin.api;

import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminNotificationReprocessResponse;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminOutboxReprocessResponse;

public interface AdminReprocessingFacade {

    AdminNotificationReprocessResponse retryNotification(Long notificationEventId);

    AdminNotificationReprocessResponse ignoreNotification(Long notificationEventId);

    AdminOutboxReprocessResponse retryOutboxDeadLetter(Long outboxEventId);
}
