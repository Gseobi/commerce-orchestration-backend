package io.github.gseobi.commerce.orchestration.payment.client;

import java.math.BigDecimal;

public interface PaymentProviderClient {

    PaymentProviderResult approve(Long orderId, BigDecimal amount, String description);

    PaymentProviderResult cancel(Long orderId, String reason);
}
