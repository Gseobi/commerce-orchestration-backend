package io.github.gseobi.commerce.orchestration.notification.entity;

public enum NotificationEventStatus {
    READY,
    REQUESTED,
    SENT,
    RETRY_SCHEDULED,
    MANUAL_INTERVENTION_REQUIRED,
    IGNORED,
    FAILED
}
