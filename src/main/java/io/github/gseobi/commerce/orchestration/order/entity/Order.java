package io.github.gseobi.commerce.orchestration.order.entity;

import io.github.gseobi.commerce.orchestration.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import lombok.Getter;

@Getter
@Entity
@Table(name = "orders")
public class Order extends BaseTimeEntity {

    private static final Map<OrderStatus, EnumSet<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.CREATED, EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.FAILED, OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_PENDING, EnumSet.of(OrderStatus.PAID, OrderStatus.FAILED, OrderStatus.CANCELLED),
            OrderStatus.PAID, EnumSet.of(OrderStatus.SETTLEMENT_REQUESTED, OrderStatus.FAILED, OrderStatus.CANCELLED),
            OrderStatus.SETTLEMENT_REQUESTED, EnumSet.of(OrderStatus.NOTIFICATION_REQUESTED, OrderStatus.FAILED, OrderStatus.CANCELLED),
            OrderStatus.NOTIFICATION_REQUESTED, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.FAILED),
            OrderStatus.FAILED, EnumSet.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class)
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String customerId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrderStatus status;

    protected Order() {
    }

    public Order(String customerId, BigDecimal totalAmount, String currency, String description, OrderStatus status) {
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.description = description;
        this.status = status;
    }

    public void transitionTo(OrderStatus nextStatus) {
        if (this.status == nextStatus) {
            return;
        }
        EnumSet<OrderStatus> allowedStatuses = ALLOWED_TRANSITIONS.getOrDefault(this.status, EnumSet.noneOf(OrderStatus.class));
        if (!allowedStatuses.contains(nextStatus)) {
            throw new IllegalStateException("허용되지 않은 주문 상태 전이입니다. current=%s, next=%s".formatted(this.status, nextStatus));
        }
        this.status = nextStatus;
    }
}
