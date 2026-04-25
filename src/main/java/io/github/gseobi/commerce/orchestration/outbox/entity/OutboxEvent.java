package io.github.gseobi.commerce.orchestration.outbox.entity;

import io.github.gseobi.commerce.orchestration.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 2000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column
    private LocalDateTime publishedAt;

    @Column(name = "dead_lettered_at")
    private LocalDateTime deadLetteredAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(length = 1000)
    private String failureReason;

    @Version
    private Long version;

    protected OutboxEvent() {
    }

    public OutboxEvent(Long orderId, String topic, String eventType, String payload, OutboxStatus status) {
        this.orderId = orderId;
        this.topic = topic;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.retryCount = 0;
        this.nextAttemptAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.lastAttemptAt = LocalDateTime.now();
        this.publishedAt = LocalDateTime.now();
        this.nextAttemptAt = null;
        this.deadLetteredAt = null;
        this.failureCode = null;
        this.failureReason = null;
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.lastAttemptAt = LocalDateTime.now();
    }

    public void markRetryWaiting(String failureCode, String failureReason, LocalDateTime nextAttemptAt) {
        this.status = OutboxStatus.RETRY_WAIT;
        this.retryCount++;
        this.lastAttemptAt = LocalDateTime.now();
        this.nextAttemptAt = nextAttemptAt;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    public void markDeadLetter(String failureCode, String failureReason) {
        this.status = OutboxStatus.DEAD_LETTER;
        this.retryCount++;
        this.lastAttemptAt = LocalDateTime.now();
        this.deadLetteredAt = LocalDateTime.now();
        this.nextAttemptAt = null;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
    }

    public void resetForAdminRetry() {
        this.status = OutboxStatus.READY;
        this.retryCount = 0;
        this.nextAttemptAt = LocalDateTime.now();
        this.lastAttemptAt = null;
        this.deadLetteredAt = null;
        this.failureCode = null;
        this.failureReason = null;
        this.publishedAt = null;
    }
}
