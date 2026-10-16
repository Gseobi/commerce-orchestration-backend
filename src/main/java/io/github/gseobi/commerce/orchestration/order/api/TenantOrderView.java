package io.github.gseobi.commerce.orchestration.order.api;

import java.math.BigDecimal;

public record TenantOrderView(
        Long merchantId,
        Long orderId,
        String status,
        BigDecimal totalAmount,
        String currency
) {
}
