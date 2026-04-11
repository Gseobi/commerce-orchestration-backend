package io.github.gseobi.commerce.orchestration.notification.entity;

import io.github.gseobi.commerce.orchestration.common.domain.BaseTimeEntity;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_events")
public class NotificationEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationEventStatus status;

    @Column(length = 100)
    private String channel;

    @Column(length = 255)
    private String payload;

    protected NotificationEvent() {
    }

    public NotificationEvent(Order order, NotificationEventStatus status, String channel, String payload) {
        this.order = order;
        this.status = status;
        this.channel = channel;
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public NotificationEventStatus getStatus() {
        return status;
    }

    public String getChannel() {
        return channel;
    }

    public String getPayload() {
        return payload;
    }
}
