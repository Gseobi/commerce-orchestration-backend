package io.github.gseobi.commerce.orchestration.orchestration.service;

import io.github.gseobi.commerce.orchestration.audit.api.AuditRecorder;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class NotificationRetryProcessor implements NotificationRetryProcessorApplication, NotificationRetrySchedulerTrigger {

    private static final int MAX_AUTO_RETRY_COUNT = 3;
    private static final int SCHEDULER_BATCH_LIMIT = Integer.MAX_VALUE;
    private static final Duration RETRY_BACKOFF = Duration.ofMinutes(5);
    private static final String TOKEN_RETRY_PERSISTENT = "FAIL_NOTIFICATION_RETRY_PERSISTENT";

    private final NotificationRetryOperations notificationRetryOperations;
    private final OrderWorkflowAccess orderWorkflowAccess;
    private final OrderRecoveryApplication orderRecoveryApplication;
    private final AuditRecorder auditRecorder;

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
        List<NotificationRetryCandidateView> dueEvents = notificationRetryOperations.findDueRetryScheduledEvents(now, limit);

        int successCount = 0;
        int rescheduledCount = 0;
        int manualRequiredCount = 0;

        for (NotificationRetryCandidateView event : dueEvents) {
            OrderExecutionView order = orderWorkflowAccess.getOrderExecutionView(event.orderId());
            if (descriptionContains(order.description(), TOKEN_RETRY_PERSISTENT)) {
                if (event.retryCount() + 1 >= MAX_AUTO_RETRY_COUNT) {
                    notificationRetryOperations.requireManualIntervention(
                            event.notificationEventId(),
                            "NOTIFICATION_RETRY_EXHAUSTED",
                            "자동 재시도 한도를 초과하여 운영자 확인이 필요합니다.",
                            now
                    );
                    auditRecorder.record(event.orderId(), "NOTIFICATION_RETRY_MANUAL_INTERVENTION_REQUIRED",
                            "notificationEventId=%s, retryCount=%s".formatted(event.notificationEventId(), event.retryCount() + 1));
                    manualRequiredCount++;
                    continue;
                }

                notificationRetryOperations.rescheduleRetry(
                        event.notificationEventId(),
                        "NOTIFICATION_TRANSIENT_FAILURE",
                        "자동 재시도 중 다시 실패하여 다음 시도로 이월합니다.",
                        now,
                        now.plus(RETRY_BACKOFF)
                );
                auditRecorder.record(event.orderId(), "NOTIFICATION_RETRY_RESCHEDULED",
                        "notificationEventId=%s, retryCount=%s, nextAttemptAt=%s"
                                .formatted(event.notificationEventId(), event.retryCount() + 1, now.plus(RETRY_BACKOFF)));
                rescheduledCount++;
                continue;
            }

            notificationRetryOperations.markRetrySucceeded(event.notificationEventId(), now);
            orderRecoveryApplication.completeAfterNotificationRecovery(event.orderId());
            auditRecorder.record(event.orderId(), "NOTIFICATION_RETRY_PROCESSED_SUCCESS",
                    "notificationEventId=%s, retryCount=%s".formatted(event.notificationEventId(), event.retryCount() + 1));
            successCount++;
        }

        return new NotificationRetryProcessingResult(
                dueEvents.size(),
                successCount,
                rescheduledCount,
                manualRequiredCount
        );
    }

    private boolean descriptionContains(String description, String token) {
        return description != null && description.contains(token);
    }
}
