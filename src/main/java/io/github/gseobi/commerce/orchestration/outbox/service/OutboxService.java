package io.github.gseobi.commerce.orchestration.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gseobi.commerce.orchestration.outbox.api.OrderEventType;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminView;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventSummary;
import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
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
class OutboxService implements OutboxApplication, OutboxAdminApplication {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisherService outboxPublisherService;
    private final ObjectMapper objectMapper;

    @Transactional
    @Override
    public void appendOrderEvent(Long orderId, OrderEventType eventType) {
        if (orderId == null || orderId <= 0 || eventType == null) {
            throw new IllegalArgumentException("Positive orderId and supported event type are required");
        }
        String topic = switch (eventType) {
            case SETTLEMENT_REQUESTED -> "commerce.settlement.requested";
            case NOTIFICATION_REQUESTED -> "commerce.notification.requested";
        };
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new OrderEventPayload(orderId, eventType.name()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Order event serialization failed", exception);
        }
        outboxEventRepository.save(new OutboxEvent(orderId, topic, eventType.name(), payload, OutboxStatus.READY));
    }

    private record OrderEventPayload(Long orderId, String eventType) {
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
                        event.getRetryCount(),
                        event.getNextAttemptAt(),
                        event.getCreatedAt(),
                        event.getLastAttemptAt(),
                        event.getPublishedAt(),
                        event.getDeadLetteredAt(),
                        event.getFailureCode(),
                        event.getFailureReason()
                ))
                .toList();
    }

    @Transactional
    @Override
    public OutboxAdminView retryDeadLetterEvent(Long outboxEventId) {
        OutboxEvent outboxEvent = outboxEventRepository.findById(outboxEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
        String previousStatus = outboxEvent.getStatus().name();

        if (outboxEvent.getStatus() != OutboxStatus.DEAD_LETTER) {
            throw new BusinessException(ErrorCode.ADMIN_REPROCESS_NOT_ALLOWED,
                    "DEAD_LETTER 상태의 outbox 이벤트만 재처리할 수 있습니다. current=" + outboxEvent.getStatus());
        }

        outboxEvent.resetForAdminRetry();
        OutboxEvent retriedEvent = outboxPublisherService.publishEvent(outboxEventId);
        return toAdminView(retriedEvent, previousStatus);
    }

    private OutboxAdminView toAdminView(OutboxEvent event, String previousStatus) {
        return new OutboxAdminView(
                event.getId(),
                event.getOrderId(),
                event.getEventType(),
                previousStatus,
                event.getStatus().name(),
                event.getRetryCount(),
                event.getNextAttemptAt(),
                event.getLastAttemptAt(),
                event.getPublishedAt(),
                event.getDeadLetteredAt(),
                event.getFailureCode(),
                event.getFailureReason()
        );
    }
}
