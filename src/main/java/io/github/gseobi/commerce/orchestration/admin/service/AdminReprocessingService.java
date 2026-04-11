package io.github.gseobi.commerce.orchestration.admin.service;

import io.github.gseobi.commerce.orchestration.admin.api.AdminReprocessingFacade;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminNotificationReprocessResponse;
import io.github.gseobi.commerce.orchestration.admin.dto.response.AdminOutboxReprocessResponse;
import io.github.gseobi.commerce.orchestration.audit.api.AuditRecorder;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminApplication;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminView;
import io.github.gseobi.commerce.orchestration.order.api.OrderRecoveryApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AdminReprocessingService implements AdminReprocessingFacade {

    private final NotificationAdminApplication notificationAdminApplication;
    private final OutboxAdminApplication outboxAdminApplication;
    private final OrderRecoveryApplication orderRecoveryApplication;
    private final AuditRecorder auditRecorder;

    @Transactional
    @Override
    public AdminNotificationReprocessResponse retryNotification(Long notificationEventId) {
        NotificationAdminView view = notificationAdminApplication.retryNotification(notificationEventId);
        orderRecoveryApplication.completeAfterNotificationRecovery(view.orderId());
        auditRecorder.record(view.orderId(), "ADMIN_NOTIFICATION_RETRIED",
                "notificationEventId=" + notificationEventId + ", status=" + view.status());
        return AdminNotificationReprocessResponse.from(view, "COMPLETED", "RETRY_NOTIFICATION");
    }

    @Transactional
    @Override
    public AdminNotificationReprocessResponse ignoreNotification(Long notificationEventId) {
        NotificationAdminView view = notificationAdminApplication.ignoreNotification(notificationEventId);
        orderRecoveryApplication.completeAfterNotificationRecovery(view.orderId());
        auditRecorder.record(view.orderId(), "ADMIN_NOTIFICATION_IGNORED",
                "notificationEventId=" + notificationEventId + ", status=" + view.status());
        return AdminNotificationReprocessResponse.from(view, "COMPLETED", "IGNORE_NOTIFICATION");
    }

    @Transactional
    @Override
    public AdminOutboxReprocessResponse retryOutboxDeadLetter(Long outboxEventId) {
        OutboxAdminView view = outboxAdminApplication.retryDeadLetterEvent(outboxEventId);
        auditRecorder.record(view.orderId(), "ADMIN_OUTBOX_RETRIED",
                "outboxEventId=" + outboxEventId + ", status=" + view.status());
        return AdminOutboxReprocessResponse.from(view, "RETRY_OUTBOX_DEAD_LETTER");
    }
}
