package io.github.gseobi.commerce.orchestration.payment.dto.response;

import io.github.gseobi.commerce.orchestration.payment.entity.Payment;
import java.math.BigDecimal;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String status,
        BigDecimal amount,
        String providerReference
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getProviderReference()
        );
    }
}
