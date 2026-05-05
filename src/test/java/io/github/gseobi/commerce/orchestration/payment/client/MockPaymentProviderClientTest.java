package io.github.gseobi.commerce.orchestration.payment.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gseobi.commerce.orchestration.config.PaymentProviderProperties;
import io.github.gseobi.commerce.orchestration.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MockPaymentProviderClientTest {

    @Test
    void approve_timeoutUnknownToken_returnsConfirmationRequired() {
        MockPaymentProviderClient client = new MockPaymentProviderClient(properties());

        PaymentProviderResult result = client.approve(
                1L,
                BigDecimal.valueOf(10000),
                "order with PAYMENT_TIMEOUT_UNKNOWN"
        );

        assertThat(result.status()).isEqualTo(PaymentStatus.CONFIRMATION_REQUIRED);
        assertThat(result.providerReference()).isEqualTo("MOCK-PAYMENT-UNKNOWN");
        assertThat(result.message()).contains("unknown");
    }

    private PaymentProviderProperties properties() {
        return new PaymentProviderProperties(
                "mock",
                "http://localhost:8089",
                "",
                "/payments/approve",
                "/payments/cancel",
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                "FAIL_PAYMENT"
        );
    }
}
