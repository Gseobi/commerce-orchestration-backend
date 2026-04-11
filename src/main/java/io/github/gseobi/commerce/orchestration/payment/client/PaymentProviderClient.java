package io.github.gseobi.commerce.orchestration.payment.client;

public interface PaymentProviderClient {

    PaymentProviderResult approve(Long orderId, String description);

    PaymentProviderResult cancel(Long orderId, String reason);
}
