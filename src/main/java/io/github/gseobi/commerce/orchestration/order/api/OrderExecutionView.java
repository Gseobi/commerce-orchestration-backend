package io.github.gseobi.commerce.orchestration.order.api;

import java.math.BigDecimal;

public record OrderExecutionView(
        Long orderId,
        String status,
        BigDecimal totalAmount,
        String description
) {
}
