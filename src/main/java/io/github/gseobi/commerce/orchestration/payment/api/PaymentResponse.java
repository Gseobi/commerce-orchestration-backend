package io.github.gseobi.commerce.orchestration.payment.api;

import java.math.BigDecimal;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String status,
        BigDecimal amount,
        String providerReference
) {

    public static PaymentResponse of(
            Long paymentId,
            Long orderId,
            String status,
            BigDecimal amount,
            String providerReference
    ) {
        return new PaymentResponse(
                paymentId,
                orderId,
                status,
                amount,
                providerReference
        );
    }
}
