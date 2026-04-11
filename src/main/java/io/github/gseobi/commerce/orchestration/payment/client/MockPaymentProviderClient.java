package io.github.gseobi.commerce.orchestration.payment.client;

import io.github.gseobi.commerce.orchestration.config.PaymentProviderProperties;
import io.github.gseobi.commerce.orchestration.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payment.provider", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProviderClient implements PaymentProviderClient {

    private final PaymentProviderProperties paymentProviderProperties;

    @Override
    public PaymentProviderResult approve(Long orderId, BigDecimal amount, String description) {
        String failureToken = paymentProviderProperties.mockFailureToken();
        if (description != null && failureToken != null && description.contains(failureToken)) {
            return new PaymentProviderResult(PaymentStatus.FAILED, "MOCK-PAYMENT-FAILED", "Simulated payment failure");
        }
        return new PaymentProviderResult(
                PaymentStatus.APPROVED,
                "MOCK-PAYMENT-%d".formatted(orderId),
                "Mock payment approved for amount=%s".formatted(amount)
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
