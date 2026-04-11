package io.github.gseobi.commerce.orchestration.outbox.api;

public interface OutboxAdminApplication {

    OutboxAdminView retryDeadLetterEvent(Long outboxEventId);
}
