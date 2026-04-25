package io.github.gseobi.commerce.orchestration.outbox.api;

import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;

public interface OutboxEventPublisher {

    OutboxPublishResult publish(OutboxEvent event);
}
