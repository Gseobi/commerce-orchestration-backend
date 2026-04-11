package io.github.gseobi.commerce.orchestration.audit.entity;

import io.github.gseobi.commerce.orchestration.common.domain.BaseTimeEntity;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 255)
    private String detail;

    protected AuditLog() {
    }

    public AuditLog(Order order, String action, String detail) {
        this.order = order;
        this.action = action;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }
}
