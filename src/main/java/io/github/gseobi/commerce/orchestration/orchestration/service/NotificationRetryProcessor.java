package io.github.gseobi.commerce.orchestration.orchestration.service;

import io.github.gseobi.commerce.orchestration.audit.api.AuditRecorder;
import io.github.gseobi.commerce.orchestration.common.metrics.CommerceRecoveryMetrics;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryCandidateView;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryOperations;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryProcessingResult;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetrySchedulerTrigger;
import io.github.gseobi.commerce.orchestration.orchestration.api.NotificationRetryProcessorApplication;
import io.github.gseobi.commerce.orchestration.order.api.OrderExecutionView;
import io.github.gseobi.commerce.orchestration.order.api.OrderRecoveryApplication;
import io.github.gseobi.commerce.orchestration.order.api.OrderWorkflowAccess;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class NotificationRetryProcessor implements NotificationRetryProcessorApplication, NotificationRetrySchedulerTrigger {

    private static final int MAX_AUTO_RETRY_COUNT = 3;
    private static final int SCHEDULER_BATCH_LIMIT = Integer.MAX_VALUE;
    private static final Duration RETRY_BACKOFF = Duration.ofMinutes(5);
    private static final String TOKEN_RETRY_PERSISTENT = "FAIL_NOTIFICATION_RETRY_PERSISTENT";
    private static final String STATUS_RETRY_SCHEDULED = "RETRY_SCHEDULED";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_MANUAL_INTERVENTION_REQUIRED = "MANUAL_INTERVENTION_REQUIRED";
    private static final String FAILURE_CODE_NONE = "none";
    private static final String FAILURE_CODE_TRANSIENT = "NOTIFICATION_TRANSIENT_FAILURE";
    private static final String FAILURE_CODE_RETRY_EXHAUSTED = "NOTIFICATION_RETRY_EXHAUSTED";

    private final NotificationRetryOperations notificationRetryOperations;
    private final OrderWorkflowAccess orderWorkflowAccess;
    private final OrderRecoveryApplication orderRecoveryApplication;
    private final AuditRecorder auditRecorder;
    private final CommerceRecoveryMetrics commerceRecoveryMetrics;

    @Transactional
    @Override
    public NotificationRetryProcessingResult processDueRetryEvents() {
        return processRetries(LocalDateTime.now(), SCHEDULER_BATCH_LIMIT);
    }

    @Transactional
    @Override
    public NotificationRetryProcessingResult processDueRetries(LocalDateTime now, int limit) {
        return processRetries(now, limit);
    }

    private NotificationRetryProcessingResult processRetries(LocalDateTime now, int limit) {
        commerceRecoveryMetrics.incrementNotificationRetryBatchStarted();
        log.info("event=notification_retry_batch_started limit={}", limit);
        List<NotificationRetryCandidateView> dueEvents = notificationRetryOperations.findDueRetryScheduledEvents(now, limit);

        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        List<Long> processedEventIds = new ArrayList<>(dueEvents.size());

        for (NotificationRetryCandidateView event : dueEvents) {
            int claimed = notificationRetryOperations.claimRetryScheduledEvent(
                    event.notificationEventId(),
                    now,
                    MAX_AUTO_RETRY_COUNT
            );
            if (claimed == 0) {
                skippedCount++;
                commerceRecoveryMetrics.incrementNotificationRetrySkipped("claim_failed");
                log.info("event=notification_retry_claim_skipped notificationEventId={} orderId={} previousStatus={} result={} reason={}",
                        event.notificationEventId(),
                        event.orderId(),
                        STATUS_RETRY_SCHEDULED,
                        "SKIPPED",
                        "claim_failed");
                continue;
            }

            processedEventIds.add(event.notificationEventId());
            OrderExecutionView order = orderWorkflowAccess.getOrderExecutionView(event.orderId());
            if (descriptionContains(order.description(), TOKEN_RETRY_PERSISTENT)) {
                if (event.retryCount() + 1 >= MAX_AUTO_RETRY_COUNT) {
                    notificationRetryOperations.requireManualIntervention(
                            event.notificationEventId(),
                            FAILURE_CODE_RETRY_EXHAUSTED,
                            "자동 재시도 한도를 초과하여 운영자 확인이 필요합니다.",
                            now
                    );
                    commerceRecoveryMetrics.incrementNotificationRetryFailed(FAILURE_CODE_RETRY_EXHAUSTED);
                    commerceRecoveryMetrics.incrementNotificationRetryManualRequired(FAILURE_CODE_RETRY_EXHAUSTED);
                    log.warn("event=notification_retry_manual_required notificationEventId={} orderId={} previousStatus={} currentStatus={} retryCount={} failureCode={} result={}",
                            event.notificationEventId(),
                            event.orderId(),
                            STATUS_RETRY_SCHEDULED,
                            STATUS_MANUAL_INTERVENTION_REQUIRED,
                            event.retryCount() + 1,
                            FAILURE_CODE_RETRY_EXHAUSTED,
                            "MANUAL_REQUIRED");
                    auditRecorder.record(event.orderId(), "NOTIFICATION_RETRY_MANUAL_INTERVENTION_REQUIRED",
                            "notificationEventId=%s, retryCount=%s".formatted(event.notificationEventId(), event.retryCount() + 1));
                    failedCount++;
                    continue;
                }

                notificationRetryOperations.rescheduleRetry(
                        event.notificationEventId(),
                        FAILURE_CODE_TRANSIENT,
                        "자동 재시도 중 다시 실패하여 다음 시도로 이월합니다.",
                        now,
                        now.plus(RETRY_BACKOFF)
                );
                commerceRecoveryMetrics.incrementNotificationRetryFailed(FAILURE_CODE_TRANSIENT);
                log.warn("event=notification_retry_rescheduled notificationEventId={} orderId={} previousStatus={} currentStatus={} retryCount={} failureCode={} result={}",
                        event.notificationEventId(),
                        event.orderId(),
                        STATUS_RETRY_SCHEDULED,
                        STATUS_RETRY_SCHEDULED,
                        event.retryCount() + 1,
                        FAILURE_CODE_TRANSIENT,
                        "RESCHEDULED");
                auditRecorder.record(event.orderId(), "NOTIFICATION_RETRY_RESCHEDULED",
                        "notificationEventId=%s, retryCount=%s, nextAttemptAt=%s"
                                .formatted(event.notificationEventId(), event.retryCount() + 1, now.plus(RETRY_BACKOFF)));
                failedCount++;
                continue;
            }

            notificationRetryOperations.markRetrySucceeded(event.notificationEventId(), now);
            orderRecoveryApplication.completeAfterNotificationRecovery(event.orderId());
            commerceRecoveryMetrics.incrementNotificationRetrySuccess(FAILURE_CODE_NONE);
            log.info("event=notification_retry_succeeded notificationEventId={} orderId={} previousStatus={} currentStatus={} retryCount={} failureCode={} result={}",
                    event.notificationEventId(),
                    event.orderId(),
                    STATUS_RETRY_SCHEDULED,
                    STATUS_SENT,
                    event.retryCount() + 1,
                    FAILURE_CODE_NONE,
                    "SUCCESS");
            auditRecorder.record(event.orderId(), "NOTIFICATION_RETRY_PROCESSED_SUCCESS",
                    "notificationEventId=%s, retryCount=%s".formatted(event.notificationEventId(), event.retryCount() + 1));
            successCount++;
        }

        log.info("event=notification_retry_batch_completed candidateCount={} successCount={} failedCount={} skippedCount={} result={}",
                dueEvents.size(),
                successCount,
                failedCount,
                skippedCount,
                "COMPLETED");
        return NotificationRetryProcessingResult.completed(
                dueEvents.size(),
                successCount,
                failedCount,
                skippedCount,
                processedEventIds
        );
    }

    private boolean descriptionContains(String description, String token) {
        return description != null && description.contains(token);
    }
}
