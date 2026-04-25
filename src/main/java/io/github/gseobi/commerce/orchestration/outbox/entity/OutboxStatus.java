package io.github.gseobi.commerce.orchestration.outbox.entity;

public enum OutboxStatus {
    READY,
    PROCESSING,
    RETRY_WAIT,
    PUBLISHED,
    DEAD_LETTER
}
