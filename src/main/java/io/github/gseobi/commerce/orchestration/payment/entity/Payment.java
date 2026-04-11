package io.github.gseobi.commerce.orchestration.payment.entity;

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
import lombok.Getter;

@Getter
@Entity
@Table(name = "payments")
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String providerReference;

    protected Payment() {
    }

    public Payment(Long orderId, PaymentStatus status, BigDecimal amount, String providerReference) {
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.providerReference = providerReference;
    }

    public void changeStatus(PaymentStatus status) {
        this.status = status;
    }

    public void changeProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }
}
