package io.github.gseobi.commerce.orchestration.orchestration.entity;

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
@Table(name = "orchestration_steps")
public class OrchestrationStep extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrchestrationStepType stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrchestrationStepStatus status;

    @Column(length = 255)
    private String detail;

    protected OrchestrationStep() {
    }

    public OrchestrationStep(Order order, OrchestrationStepType stepType, OrchestrationStepStatus status, String detail) {
        this.order = order;
        this.stepType = stepType;
        this.status = status;
        this.detail = detail;
    }

    public void changeStatus(OrchestrationStepStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public OrchestrationStepType getStepType() {
        return stepType;
    }

    public OrchestrationStepStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
