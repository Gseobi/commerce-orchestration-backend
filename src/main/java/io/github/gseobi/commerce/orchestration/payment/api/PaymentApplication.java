package io.github.gseobi.commerce.orchestration.payment.api;

import io.github.gseobi.commerce.orchestration.payment.dto.response.PaymentResponse;
import java.math.BigDecimal;
import java.util.List;

public interface PaymentApplication {

    PaymentResponse approve(Long orderId, BigDecimal amount, String description);

    PaymentResponse cancelLatestApprovedPayment(Long orderId, String reason);

    List<String> getPaymentStatuses(Long orderId);
}
