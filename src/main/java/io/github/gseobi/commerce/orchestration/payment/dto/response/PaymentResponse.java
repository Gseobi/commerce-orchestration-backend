package io.github.gseobi.commerce.orchestration.payment.dto.response;

import io.github.gseobi.commerce.orchestration.payment.entity.Payment;
import io.github.gseobi.commerce.orchestration.payment.entity.PaymentStatus;
import java.math.BigDecimal;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        PaymentStatus status,
        BigDecimal amount,
        String providerReference
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getProviderReference()
        );
    }
}
