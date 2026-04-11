package io.github.gseobi.commerce.orchestration.order.dto.request;

import java.math.BigDecimal;

public record CreateOrderRequest(
        String customerId,
        BigDecimal totalAmount,
        String currency,
        String description
) {
}
