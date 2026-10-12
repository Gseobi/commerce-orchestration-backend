package io.github.gseobi.commerce.orchestration.outbox.api;

public record OutboxPublishMessage(Long orderId, String topic, String eventType, String payload) {
}
