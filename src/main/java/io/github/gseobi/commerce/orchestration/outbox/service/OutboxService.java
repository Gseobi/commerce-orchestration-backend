package io.github.gseobi.commerce.orchestration.outbox.service;

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
