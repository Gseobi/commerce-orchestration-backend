package io.github.gseobi.commerce.orchestration.notification.api;

public interface NotificationAdminApplication {

    NotificationAdminView retryNotification(Long notificationEventId);

    NotificationAdminView ignoreNotification(Long notificationEventId);
}
