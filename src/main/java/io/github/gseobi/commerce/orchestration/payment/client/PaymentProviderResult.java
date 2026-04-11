package io.github.gseobi.commerce.orchestration.payment.client;

import io.github.gseobi.commerce.orchestration.payment.entity.PaymentStatus;

public record PaymentProviderResult(
        PaymentStatus status,
        String providerReference,
        String message
) {
}
