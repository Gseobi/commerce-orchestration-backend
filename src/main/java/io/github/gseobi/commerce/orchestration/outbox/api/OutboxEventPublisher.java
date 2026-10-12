package io.github.gseobi.commerce.orchestration.outbox.api;

public interface OutboxEventPublisher {

    OutboxPublishResult publish(OutboxPublishMessage message);
}
