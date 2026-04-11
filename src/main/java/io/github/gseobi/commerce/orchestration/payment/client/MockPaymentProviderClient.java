package io.github.gseobi.commerce.orchestration.payment.client;

import io.github.gseobi.commerce.orchestration.payment.entity.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentProviderClient implements PaymentProviderClient {

    private static final String FAIL_PAYMENT_TOKEN = "FAIL_PAYMENT";

    @Override
    public PaymentProviderResult approve(Long orderId, String description) {
        if (description != null && description.contains(FAIL_PAYMENT_TOKEN)) {
            return new PaymentProviderResult(PaymentStatus.FAILED, "MOCK-PAYMENT-FAILED", "Simulated payment failure");
        }
        return new PaymentProviderResult(
                PaymentStatus.APPROVED,
                "MOCK-PAYMENT-%d".formatted(orderId),
                "Mock payment approved"
        );
    }

    @Override
    public PaymentProviderResult cancel(Long orderId, String reason) {
        return new PaymentProviderResult(
                PaymentStatus.CANCELLED,
                "MOCK-CANCEL-%d".formatted(orderId),
                "Mock cancel completed: " + reason
        );
    }
}
