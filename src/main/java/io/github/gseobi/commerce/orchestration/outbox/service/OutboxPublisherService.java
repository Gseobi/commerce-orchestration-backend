package io.github.gseobi.commerce.orchestration.outbox.service;

import io.github.gseobi.commerce.orchestration.config.OutboxProperties;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboxPublisherService {

    private static final List<OutboxStatus> PUBLISHABLE_STATUSES = List.of(OutboxStatus.READY, OutboxStatus.RETRY_WAIT);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties outboxProperties;

    @Transactional
    public int publishReadyEvents(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> readyEvents = outboxEventRepository.findPublishableEvents(
                PUBLISHABLE_STATUSES,
                now,
                PageRequest.of(0, batchSize)
        );
        int publishedCount = 0;
        for (OutboxEvent readyEvent : readyEvents) {
            try {
                SendResult<String, String> result = kafkaTemplate
                        .send(readyEvent.getTopic(), String.valueOf(readyEvent.getOrderId()), readyEvent.getPayload())
                        .get(outboxProperties.publishTimeout().toSeconds(), TimeUnit.SECONDS);
                if (result != null) {
                    readyEvent.markPublished();
                    publishedCount++;
                }
            } catch (Exception exception) {
                handlePublishFailure(readyEvent, exception);
            }
        }
        return publishedCount;
    }

    private void handlePublishFailure(OutboxEvent outboxEvent, Exception exception) {
        String failureCode = exception.getClass().getSimpleName();
        String failureReason = buildFailureReason(exception);
        int nextRetryCount = outboxEvent.getRetryCount() + 1;

        if (nextRetryCount >= outboxProperties.maxRetryCount()) {
            outboxEvent.markDeadLetter(failureCode, failureReason);
            return;
        }

        Duration backoff = calculateBackoff(nextRetryCount);
        outboxEvent.markRetryWaiting(failureCode, failureReason, LocalDateTime.now().plus(backoff));
    }

    private Duration calculateBackoff(int retryCount) {
        double multiplier = Math.pow(outboxProperties.backoffMultiplier(), Math.max(0, retryCount - 1));
        long initialMillis = outboxProperties.initialBackoff().toMillis();
        long calculatedMillis = Math.round(initialMillis * multiplier);
        long cappedMillis = Math.min(calculatedMillis, outboxProperties.maxBackoff().toMillis());
        return Duration.ofMillis(Math.max(cappedMillis, 0));
    }

    private String buildFailureReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
