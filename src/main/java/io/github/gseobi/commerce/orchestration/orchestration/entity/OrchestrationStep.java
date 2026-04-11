package io.github.gseobi.commerce.orchestration.orchestration.entity;

import io.github.gseobi.commerce.orchestration.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "orchestration_steps")
public class OrchestrationStep extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

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

    public OrchestrationStep(Long orderId, OrchestrationStepType stepType, OrchestrationStepStatus status, String detail) {
        this.orderId = orderId;
        this.stepType = stepType;
        this.status = status;
        this.detail = detail;
    }

    public void changeStatus(OrchestrationStepStatus status) {
        this.status = status;
    }
}
