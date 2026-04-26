package io.github.gseobi.commerce.orchestration.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.audit.api.AuditRecorder;
import io.github.gseobi.commerce.orchestration.common.metrics.CommerceRecoveryMetrics;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminApplication;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminView;
import io.github.gseobi.commerce.orchestration.order.api.OrderRecoveryApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminReprocessingServiceTest {

    @Mock
    private NotificationAdminApplication notificationAdminApplication;

    @Mock
    private OutboxAdminApplication outboxAdminApplication;

    @Mock
    private OrderRecoveryApplication orderRecoveryApplication;

    @Mock
    private AuditRecorder auditRecorder;

    private SimpleMeterRegistry meterRegistry;
    private AdminReprocessingService adminReprocessingService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        adminReprocessingService = new AdminReprocessingService(
                notificationAdminApplication,
                outboxAdminApplication,
                orderRecoveryApplication,
                auditRecorder,
                new CommerceRecoveryMetrics(meterRegistry)
        );
    }

    @Test
    void retryNotification_recordsMetricsAndStructuredAuditDetail() {
        NotificationAdminView view = notificationView("RETRY_SCHEDULED", "SENT");
        when(notificationAdminApplication.retryNotification(10L)).thenReturn(view);

        adminReprocessingService.retryNotification(10L);

        assertThat(adminCounter("commerce.admin.recovery.requests", "notification", "RETRY")).isEqualTo(1.0);
        assertThat(adminCounter("commerce.admin.recovery.success", "notification", "RETRY")).isEqualTo(1.0);
        verify(orderRecoveryApplication).completeAfterNotificationRecovery(20L);

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq("ADMIN_NOTIFICATION_RETRIED"),
                detailCaptor.capture());
        assertThat(detailCaptor.getValue())
                .contains("action=RETRY")
                .contains("result=SUCCESS")
                .contains("previousStatus=RETRY_SCHEDULED")
                .contains("currentStatus=SENT")
                .contains("notificationEventId=10");
    }

    @Test
    void ignoreNotification_recordsMetrics() {
        when(notificationAdminApplication.ignoreNotification(10L))
                .thenReturn(notificationView("MANUAL_INTERVENTION_REQUIRED", "IGNORED"));

        adminReprocessingService.ignoreNotification(10L);

        assertThat(adminCounter("commerce.admin.recovery.requests", "notification", "IGNORE")).isEqualTo(1.0);
        assertThat(adminCounter("commerce.admin.recovery.success", "notification", "IGNORE")).isEqualTo(1.0);
    }

    @Test
    void retryOutboxDeadLetter_recordsMetricsAndAuditDetail() {
        OutboxAdminView view = new OutboxAdminView(
                30L,
                20L,
                "NOTIFICATION",
                "DEAD_LETTER",
                "PUBLISHED",
                0,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null
        );
        when(outboxAdminApplication.retryDeadLetterEvent(30L)).thenReturn(view);

        adminReprocessingService.retryOutboxDeadLetter(30L);

        assertThat(adminCounter("commerce.admin.recovery.requests", "outbox", "RETRY_DEAD_LETTER")).isEqualTo(1.0);
        assertThat(adminCounter("commerce.admin.recovery.success", "outbox", "RETRY_DEAD_LETTER")).isEqualTo(1.0);

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq("ADMIN_OUTBOX_RETRIED"),
                detailCaptor.capture());
        assertThat(detailCaptor.getValue())
                .contains("action=RETRY_DEAD_LETTER")
                .contains("result=PUBLISHED")
                .contains("previousStatus=DEAD_LETTER")
                .contains("currentStatus=PUBLISHED")
                .contains("outboxEventId=30");
    }

    @Test
    void retryOutboxDeadLetter_recordsFailureMetricAndRethrows() {
        when(outboxAdminApplication.retryDeadLetterEvent(30L))
                .thenThrow(new IllegalStateException("not retryable"));

        assertThatThrownBy(() -> adminReprocessingService.retryOutboxDeadLetter(30L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(adminCounter("commerce.admin.recovery.requests", "outbox", "RETRY_DEAD_LETTER")).isEqualTo(1.0);
        assertThat(meterRegistry.find("commerce.admin.recovery.failure")
                .tag("target", "outbox")
                .tag("action", "RETRY_DEAD_LETTER")
                .tag("failureCode", "IllegalStateException")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    private NotificationAdminView notificationView(String previousStatus, String status) {
        return new NotificationAdminView(
                10L,
                20L,
                previousStatus,
                status,
                "NONE",
                1,
                null,
                LocalDateTime.now(),
                null,
                null
        );
    }

    private double adminCounter(String name, String target, String action) {
        return meterRegistry.find(name)
                .tag("target", target)
                .tag("action", action)
                .counter()
                .count();
    }
}
