package io.github.gseobi.commerce.orchestration.order.api;

import java.math.BigDecimal;

/** Internal storage port for the verified approval transaction. */
public interface TenantOrderPaymentTransition {

    void markPaid(long merchantId, long orderId, BigDecimal amount, String currency);
}
