package io.github.gseobi.commerce.orchestration.outbox.service;

import io.github.gseobi.commerce.orchestration.outbox.api.OutboxApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventSummary;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class OutboxService implements OutboxApplication {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    @Override
    public void appendOrderEvent(Long orderId, String topic, String eventType, String payload) {
        outboxEventRepository.save(new OutboxEvent(orderId, topic, eventType, payload, OutboxStatus.READY));
    }

    @Override
    public List<OutboxEventSummary> getOrderEvents(Long orderId) {
        return outboxEventRepository.findAllByOrderIdOrderByIdAsc(orderId)
                .stream()
                .map(event -> new OutboxEventSummary(
                        event.getId(),
                        event.getTopic(),
                        event.getEventType(),
                        event.getStatus().name(),
                        event.getPayload(),
                        event.getCreatedAt(),
                        event.getPublishedAt(),
                        event.getFailureReason()
                ))
                .toList();
    }
}
