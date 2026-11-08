package io.github.gseobi.commerce.orchestration.order.api;

import java.math.BigDecimal;

public record CreateTenantOrderCommand(Long storeId, String customerId, BigDecimal totalAmount, String currency) {

    @Override
    public String toString() {
        return "CreateTenantOrderCommand[redacted]";
    }
}
