package io.github.gseobi.commerce.orchestration.outbox.api;

import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventSummary;
import java.util.List;

public interface OutboxApplication {

    void appendOrderEvent(Long orderId, OrderEventType eventType);

    List<OutboxEventSummary> getOrderEvents(Long orderId);
}
