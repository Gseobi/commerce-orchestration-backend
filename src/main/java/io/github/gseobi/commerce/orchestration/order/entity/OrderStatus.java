package io.github.gseobi.commerce.orchestration.order.entity;

public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    SETTLEMENT_REQUESTED,
    NOTIFICATION_REQUESTED,
    COMPLETED,
    FAILED,
    CANCELLED
}
