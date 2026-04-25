package io.github.gseobi.commerce.orchestration.outbox.service;

import io.github.gseobi.commerce.orchestration.config.OutboxProperties;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventPublisher;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxPublishResult;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboxPublisherService {

    private static final List<OutboxStatus> PUBLISHABLE_STATUSES = List.of(OutboxStatus.READY, OutboxStatus.RETRY_WAIT);

    private final OutboxEventPublisher outboxEventPublisher;
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
        for (OutboxEvent candidateEvent : readyEvents) {
            int claimed = outboxEventRepository.claimPublishableEvent(
                    candidateEvent.getId(),
                    PUBLISHABLE_STATUSES,
                    now
            );
            if (claimed == 0) {
                continue;
            }

            OutboxEvent claimedEvent = outboxEventRepository.findById(candidateEvent.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
            publishEvent(claimedEvent);
            if (claimedEvent.getStatus() == OutboxStatus.PUBLISHED) {
                publishedCount++;
            }
        }
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
        OutboxPublishResult result = outboxEventPublisher.publish(outboxEvent);
        if (result.isSuccess()) {
            outboxEvent.markPublished();
            return;
        }
        handlePublishFailure(outboxEvent, result);
    }

    private void handlePublishFailure(OutboxEvent outboxEvent, OutboxPublishResult result) {
        String failureCode = result.failureCode();
        String failureReason = result.failureReason();
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
}
