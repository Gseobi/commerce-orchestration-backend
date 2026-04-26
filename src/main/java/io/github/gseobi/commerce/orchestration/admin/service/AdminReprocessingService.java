package io.github.gseobi.commerce.orchestration.admin.service;

import io.github.gseobi.commerce.orchestration.admin.api.AdminReprocessingFacade;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminNotificationReprocessResponse;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminOutboxReprocessResponse;
import io.github.gseobi.commerce.orchestration.audit.api.AuditRecorder;
import io.github.gseobi.commerce.orchestration.common.metrics.CommerceRecoveryMetrics;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminApplication;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminView;
import io.github.gseobi.commerce.orchestration.order.api.OrderRecoveryApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AdminReprocessingService implements AdminReprocessingFacade {

    private static final String TARGET_NOTIFICATION = "notification";
    private static final String TARGET_OUTBOX = "outbox";
    private static final String ACTION_RETRY = "RETRY";
    private static final String ACTION_IGNORE = "IGNORE";
    private static final String ACTION_RETRY_DEAD_LETTER = "RETRY_DEAD_LETTER";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_IGNORED = "IGNORED";

    private final NotificationAdminApplication notificationAdminApplication;
    private final OutboxAdminApplication outboxAdminApplication;
    private final OrderRecoveryApplication orderRecoveryApplication;
    private final AuditRecorder auditRecorder;
    private final CommerceRecoveryMetrics commerceRecoveryMetrics;

    @Transactional
    @Override
    public AdminNotificationReprocessResponse retryNotification(Long notificationEventId) {
        commerceRecoveryMetrics.incrementAdminRecoveryRequest(TARGET_NOTIFICATION, ACTION_RETRY);
        log.info("event=admin_notification_retry_requested notificationEventId={} action={}",
                notificationEventId,
                ACTION_RETRY);
        try {
            NotificationAdminView view = notificationAdminApplication.retryNotification(notificationEventId);
            orderRecoveryApplication.completeAfterNotificationRecovery(view.orderId());
            auditRecorder.record(view.orderId(), "ADMIN_NOTIFICATION_RETRIED",
                    notificationAuditDetail(notificationEventId, ACTION_RETRY, RESULT_SUCCESS, view));
            commerceRecoveryMetrics.incrementAdminRecoverySuccess(TARGET_NOTIFICATION, ACTION_RETRY);
            log.info("event=admin_notification_retry_completed notificationEventId={} orderId={} action={} result={} previousStatus={} currentStatus={}",
                    notificationEventId,
                    view.orderId(),
                    ACTION_RETRY,
                    RESULT_SUCCESS,
                    view.previousStatus(),
                    view.status());
            return AdminNotificationReprocessResponse.from(
                    view,
                    "COMPLETED",
                    ACTION_RETRY,
                    RESULT_SUCCESS,
                    "Notification event was retried successfully."
            );
        } catch (RuntimeException exception) {
            recordAdminFailure(TARGET_NOTIFICATION, ACTION_RETRY, notificationEventId, exception);
            throw exception;
        }
    }

    @Transactional
    @Override
    public AdminNotificationReprocessResponse ignoreNotification(Long notificationEventId) {
        commerceRecoveryMetrics.incrementAdminRecoveryRequest(TARGET_NOTIFICATION, ACTION_IGNORE);
        log.info("event=admin_notification_ignore_requested notificationEventId={} action={}",
                notificationEventId,
                ACTION_IGNORE);
        try {
            NotificationAdminView view = notificationAdminApplication.ignoreNotification(notificationEventId);
            orderRecoveryApplication.completeAfterNotificationRecovery(view.orderId());
            auditRecorder.record(view.orderId(), "ADMIN_NOTIFICATION_IGNORED",
                    notificationAuditDetail(notificationEventId, ACTION_IGNORE, RESULT_IGNORED, view));
            commerceRecoveryMetrics.incrementAdminRecoverySuccess(TARGET_NOTIFICATION, ACTION_IGNORE);
            log.info("event=admin_notification_ignore_completed notificationEventId={} orderId={} action={} result={} previousStatus={} currentStatus={}",
                    notificationEventId,
                    view.orderId(),
                    ACTION_IGNORE,
                    RESULT_IGNORED,
                    view.previousStatus(),
                    view.status());
            return AdminNotificationReprocessResponse.from(
                    view,
                    "COMPLETED",
                    ACTION_IGNORE,
                    RESULT_IGNORED,
                    "Notification event was marked as ignored."
            );
        } catch (RuntimeException exception) {
            recordAdminFailure(TARGET_NOTIFICATION, ACTION_IGNORE, notificationEventId, exception);
            throw exception;
        }
    }

    @Transactional
    @Override
    public AdminOutboxReprocessResponse retryOutboxDeadLetter(Long outboxEventId) {
        commerceRecoveryMetrics.incrementAdminRecoveryRequest(TARGET_OUTBOX, ACTION_RETRY_DEAD_LETTER);
        log.info("event=admin_outbox_retry_requested outboxEventId={} action={}",
                outboxEventId,
                ACTION_RETRY_DEAD_LETTER);
        try {
            OutboxAdminView view = outboxAdminApplication.retryDeadLetterEvent(outboxEventId);
            String result = resolveOutboxRetryResult(view);
            auditRecorder.record(view.aggregateId(), "ADMIN_OUTBOX_RETRIED",
                    outboxAuditDetail(outboxEventId, ACTION_RETRY_DEAD_LETTER, result, view));
            commerceRecoveryMetrics.incrementAdminRecoverySuccess(TARGET_OUTBOX, ACTION_RETRY_DEAD_LETTER);
            log.info("event=admin_outbox_retry_completed outboxEventId={} aggregateId={} eventType={} action={} result={} previousStatus={} currentStatus={} retryCount={} failureCode={}",
                    outboxEventId,
                    view.aggregateId(),
                    view.eventType(),
                    ACTION_RETRY_DEAD_LETTER,
                    result,
                    view.previousStatus(),
                    view.status(),
                    view.retryCount(),
                    view.failureCode());
            return AdminOutboxReprocessResponse.from(
                    view,
                    ACTION_RETRY_DEAD_LETTER,
                    result,
                    resolveOutboxRetryMessage(view)
            );
        } catch (RuntimeException exception) {
            recordAdminFailure(TARGET_OUTBOX, ACTION_RETRY_DEAD_LETTER, outboxEventId, exception);
            throw exception;
        }
    }

    private String resolveOutboxRetryResult(OutboxAdminView view) {
        return view.status();
    }

    private String resolveOutboxRetryMessage(OutboxAdminView view) {
        return switch (view.status()) {
            case "PUBLISHED" -> "Outbox event was republished successfully.";
            case "RETRY_WAIT" -> "Outbox event republish failed and was rescheduled for retry.";
            case "DEAD_LETTER" -> "Outbox event republish failed and remains dead-lettered.";
            default -> "Outbox event retry finished with status " + view.status() + ".";
        };
    }

    private void recordAdminFailure(String target, String action, Long targetId, RuntimeException exception) {
        String failureCode = exception.getClass().getSimpleName();
        commerceRecoveryMetrics.incrementAdminRecoveryFailure(target, action, failureCode);
        log.warn("event=admin_recovery_failed target={} targetId={} action={} result={} failureCode={}",
                target,
                targetId,
                action,
                "FAILED",
                failureCode);
    }

    private String notificationAuditDetail(
            Long notificationEventId,
            String action,
            String result,
            NotificationAdminView view
    ) {
        return "action=%s, result=%s, previousStatus=%s, currentStatus=%s, notificationEventId=%s"
                .formatted(action, result, view.previousStatus(), view.status(), notificationEventId);
    }

    private String outboxAuditDetail(
            Long outboxEventId,
            String action,
            String result,
            OutboxAdminView view
    ) {
        return "action=%s, result=%s, previousStatus=%s, currentStatus=%s, outboxEventId=%s"
                .formatted(action, result, view.previousStatus(), view.status(), outboxEventId);
    }
}
