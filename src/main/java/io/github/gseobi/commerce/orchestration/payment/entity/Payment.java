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
import jakarta.persistence.Version;
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

    @Column(name = "payment_request_id", length = 100)
    private String paymentRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String providerReference;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Version
    private Long version;

    protected Payment() {
    }

    public Payment(Long orderId, PaymentStatus status, BigDecimal amount, String providerReference) {
        this(null, orderId, status, amount, providerReference, null);
    }

    public Payment(
            String paymentRequestId,
            Long orderId,
            PaymentStatus status,
            BigDecimal amount,
            String providerReference,
            String providerTransactionId
    ) {
        this.paymentRequestId = paymentRequestId;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.providerReference = providerReference;
        this.providerTransactionId = providerTransactionId;
    }

    public static Payment requested(
            String paymentRequestId,
            Long orderId,
            PaymentStatus status,
            BigDecimal amount,
            String providerReference
    ) {
        return new Payment(paymentRequestId, orderId, status, amount, providerReference, null);
    }

    public void changeStatus(PaymentStatus status) {
        this.status = status;
    }

    public void changeProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public void changeProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }
}
