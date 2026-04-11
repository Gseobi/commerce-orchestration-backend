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

    @Column
    private LocalDateTime publishedAt;

    @Column(length = 500)
    private String failureReason;

    protected OutboxEvent() {
    }

    public OutboxEvent(Long orderId, String topic, String eventType, String payload, OutboxStatus status) {
        this.orderId = orderId;
        this.topic = topic;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.failureReason = null;
    }

    public void markFailed(String failureReason) {
        this.status = OutboxStatus.FAILED;
        this.failureReason = failureReason;
    }
}
