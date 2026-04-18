package io.github.gseobi.commerce.orchestration.notification.entity;

import io.github.gseobi.commerce.orchestration.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "notification_events")
public class NotificationEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationEventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "handling_policy", nullable = false, length = 50)
    private NotificationHandlingPolicy handlingPolicy;

    @Column(length = 100)
    private String channel;

    @Column(length = 255)
    private String payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected NotificationEvent() {
    }

    public NotificationEvent(Long orderId, NotificationEventStatus status, String channel, String payload) {
        this.orderId = orderId;
        this.status = status;
        this.handlingPolicy = NotificationHandlingPolicy.NONE;
        this.channel = channel;
        this.payload = payload;
        this.retryCount = 0;
    }

    public void scheduleRetry(String failureCode, String failureReason, LocalDateTime nextAttemptAt) {
        scheduleRetry(failureCode, failureReason, LocalDateTime.now(), nextAttemptAt);
    }

    public void scheduleRetry(
            String failureCode,
            String failureReason,
            LocalDateTime attemptedAt,
            LocalDateTime nextAttemptAt
    ) {
        this.status = NotificationEventStatus.RETRY_SCHEDULED;
        this.handlingPolicy = NotificationHandlingPolicy.AUTO_RETRY;
        this.retryCount++;
        this.lastAttemptAt = attemptedAt;
        this.nextAttemptAt = nextAttemptAt;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    public void requireManualIntervention(String failureCode, String failureReason) {
        requireManualIntervention(failureCode, failureReason, LocalDateTime.now());
    }

    public void requireManualIntervention(
            String failureCode,
            String failureReason,
            LocalDateTime attemptedAt
    ) {
        this.status = NotificationEventStatus.MANUAL_INTERVENTION_REQUIRED;
        this.handlingPolicy = NotificationHandlingPolicy.MANUAL_INTERVENTION;
        this.retryCount++;
        this.lastAttemptAt = attemptedAt;
        this.nextAttemptAt = null;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    public void markIgnored(String failureCode, String failureReason) {
        this.status = NotificationEventStatus.IGNORED;
        this.handlingPolicy = NotificationHandlingPolicy.IGNORE;
        this.lastAttemptAt = LocalDateTime.now();
        this.nextAttemptAt = null;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    public void markSentByAdminRetry() {
        markSent(LocalDateTime.now());
    }

    public void markSent(LocalDateTime attemptedAt) {
        this.status = NotificationEventStatus.SENT;
        this.handlingPolicy = NotificationHandlingPolicy.NONE;
        this.retryCount++;
        this.lastAttemptAt = attemptedAt;
        this.nextAttemptAt = null;
        this.failureCode = null;
        this.failureReason = null;
    }

    public void markIgnoredByAdmin() {
        this.status = NotificationEventStatus.IGNORED;
        this.handlingPolicy = NotificationHandlingPolicy.IGNORE;
        this.lastAttemptAt = LocalDateTime.now();
        this.nextAttemptAt = null;
    }
}
