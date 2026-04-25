package io.github.gseobi.commerce.orchestration.notification.entity;

public enum NotificationEventStatus {
    READY,
    REQUESTED,
    PROCESSING,
    SENT,
    RETRY_SCHEDULED,
    MANUAL_INTERVENTION_REQUIRED,
    IGNORED,
    FAILED
}
