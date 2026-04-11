package io.github.gseobi.commerce.orchestration.outbox.api;

import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventSummary;
import java.util.List;

public interface OutboxApplication {

    void appendOrderEvent(Long orderId, String topic, String eventType, String payload);

    List<OutboxEventSummary> getOrderEvents(Long orderId);
}
