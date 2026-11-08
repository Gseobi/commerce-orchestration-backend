package io.github.gseobi.commerce.orchestration.payment.api;

import java.math.BigDecimal;

/** Internal only. Uses the owner, order and amount returned by a successful claim in the current transaction. */
public interface TenantPaymentRecords {

    long insertReady(long merchantId, long orderId, BigDecimal amount);
}
