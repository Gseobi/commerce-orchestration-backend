package io.github.gseobi.commerce.orchestration.outbox.service;

import io.github.gseobi.commerce.orchestration.config.OutboxProperties;
import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.common.metrics.CommerceRecoveryMetrics;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventPublisher;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxPublishResult;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboxPublisherService {

    private static final List<OutboxStatus> PUBLISHABLE_STATUSES = List.of(OutboxStatus.READY, OutboxStatus.RETRY_WAIT);

    private final OutboxEventPublisher outboxEventPublisher;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties outboxProperties;
    private final CommerceRecoveryMetrics commerceRecoveryMetrics;

    @Transactional
    public int publishReadyEvents(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        log.info("event=outbox_publish_batch_started batchSize={}", batchSize);
        List<OutboxEvent> readyEvents = outboxEventRepository.findPublishableEvents(
                PUBLISHABLE_STATUSES,
                now,
                PageRequest.of(0, batchSize)
        );
        int publishedCount = 0;
        for (OutboxEvent candidateEvent : readyEvents) {
            int claimed = outboxEventRepository.claimPublishableEvent(
                    candidateEvent.getId(),
                    PUBLISHABLE_STATUSES,
                    now
            );
            if (claimed == 0) {
                commerceRecoveryMetrics.incrementOutboxPublishSkipped("claim_failed");
                log.info("event=outbox_publish_claim_skipped eventId={} orderId={} eventType={} topic={} currentStatus={} reason={}",
                        candidateEvent.getId(),
                        candidateEvent.getOrderId(),
                        candidateEvent.getEventType(),
                        candidateEvent.getTopic(),
                        candidateEvent.getStatus(),
                        "claim_failed");
                continue;
            }

            OutboxEvent claimedEvent = outboxEventRepository.findById(candidateEvent.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
            publishEvent(claimedEvent);
            if (claimedEvent.getStatus() == OutboxStatus.PUBLISHED) {
                publishedCount++;
            }
        }
        log.info("event=outbox_publish_batch_completed batchSize={} candidateCount={} publishedCount={}",
                batchSize,
                readyEvents.size(),
                publishedCount);
        return publishedCount;
    }

    @Transactional
    public OutboxEvent publishEvent(Long outboxEventId) {
        OutboxEvent outboxEvent = outboxEventRepository.findById(outboxEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));

        if (outboxEvent.getStatus() == OutboxStatus.PUBLISHED || outboxEvent.getStatus() == OutboxStatus.PROCESSING) {
            return outboxEvent;
        }

        if (outboxEvent.getStatus() == OutboxStatus.DEAD_LETTER) {
            outboxEvent.resetForAdminRetry();
        }

        int claimed = outboxEventRepository.claimPublishableEvent(
                outboxEventId,
                PUBLISHABLE_STATUSES,
                LocalDateTime.now()
        );
        if (claimed == 0) {
            return outboxEventRepository.findById(outboxEventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
        }

        outboxEvent = outboxEventRepository.findById(outboxEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
        publishEvent(outboxEvent);
        return outboxEvent;
    }

    private void publishEvent(OutboxEvent outboxEvent) {
        OutboxStatus previousStatus = outboxEvent.getStatus();
        commerceRecoveryMetrics.incrementOutboxPublishAttempt(outboxEvent.getEventType(), outboxEvent.getTopic());
        OutboxPublishResult result;
        try {
            result = outboxEventPublisher.publish(outboxEvent);
        } catch (RuntimeException exception) {
            String failureCode = exception.getClass().getSimpleName();
            commerceRecoveryMetrics.incrementOutboxPublishFailure(
                    outboxEvent.getEventType(),
                    outboxEvent.getTopic(),
                    failureCode);
            log.warn("event=outbox_publish_unexpected_failure eventId={} orderId={} eventType={} topic={} previousStatus={} failureCode={}",
                    outboxEvent.getId(),
                    outboxEvent.getOrderId(),
                    outboxEvent.getEventType(),
                    outboxEvent.getTopic(),
                    previousStatus,
                    failureCode);
            throw exception;
        }
        if (result.isSuccess()) {
            outboxEvent.markPublished();
            commerceRecoveryMetrics.incrementOutboxPublishSuccess(outboxEvent.getEventType(), outboxEvent.getTopic());
            log.info("event=outbox_publish_completed eventId={} orderId={} eventType={} topic={} previousStatus={} currentStatus={} retryCount={}",
                    outboxEvent.getId(),
                    outboxEvent.getOrderId(),
                    outboxEvent.getEventType(),
                    outboxEvent.getTopic(),
                    previousStatus,
                    outboxEvent.getStatus(),
                    outboxEvent.getRetryCount());
            return;
        }
        handlePublishFailure(outboxEvent, result, previousStatus);
    }

    private void handlePublishFailure(OutboxEvent outboxEvent, OutboxPublishResult result, OutboxStatus previousStatus) {
        String failureCode = result.failureCode();
        String failureReason = result.failureReason();
        int nextRetryCount = outboxEvent.getRetryCount() + 1;

        if (nextRetryCount >= outboxProperties.maxRetryCount()) {
            outboxEvent.markDeadLetter(failureCode, failureReason);
            commerceRecoveryMetrics.incrementOutboxPublishFailure(
                    outboxEvent.getEventType(),
                    outboxEvent.getTopic(),
                    failureCode);
            commerceRecoveryMetrics.incrementOutboxDeadLetter(
                    outboxEvent.getEventType(),
                    outboxEvent.getTopic(),
                    failureCode);
            log.warn("event=outbox_publish_dead_lettered eventId={} orderId={} eventType={} topic={} previousStatus={} currentStatus={} retryCount={} failureCode={}",
                    outboxEvent.getId(),
                    outboxEvent.getOrderId(),
                    outboxEvent.getEventType(),
                    outboxEvent.getTopic(),
                    previousStatus,
                    outboxEvent.getStatus(),
                    outboxEvent.getRetryCount(),
                    failureCode);
            return;
        }

        Duration backoff = calculateBackoff(nextRetryCount);
        outboxEvent.markRetryWaiting(failureCode, failureReason, LocalDateTime.now().plus(backoff));
        commerceRecoveryMetrics.incrementOutboxPublishFailure(
                outboxEvent.getEventType(),
                outboxEvent.getTopic(),
                failureCode);
        log.warn("event=outbox_publish_retry_scheduled eventId={} orderId={} eventType={} topic={} previousStatus={} currentStatus={} retryCount={} failureCode={}",
                outboxEvent.getId(),
                outboxEvent.getOrderId(),
                outboxEvent.getEventType(),
                outboxEvent.getTopic(),
                previousStatus,
                outboxEvent.getStatus(),
                outboxEvent.getRetryCount(),
                failureCode);
    }

    private Duration calculateBackoff(int retryCount) {
        double multiplier = Math.pow(outboxProperties.backoffMultiplier(), Math.max(0, retryCount - 1));
        long initialMillis = outboxProperties.initialBackoff().toMillis();
        long calculatedMillis = Math.round(initialMillis * multiplier);
        long cappedMillis = Math.min(calculatedMillis, outboxProperties.maxBackoff().toMillis());
        return Duration.ofMillis(Math.max(cappedMillis, 0));
    }
}
