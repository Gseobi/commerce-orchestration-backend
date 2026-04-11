package io.github.gseobi.commerce.orchestration.settlement.entity;

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
@Table(name = "settlements")
public class Settlement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SettlementStatus status;

    @Column(length = 255)
    private String memo;

    protected Settlement() {
    }

    public Settlement(Order order, SettlementStatus status, String memo) {
        this.order = order;
        this.status = status;
        this.memo = memo;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public String getMemo() {
        return memo;
    }
}
