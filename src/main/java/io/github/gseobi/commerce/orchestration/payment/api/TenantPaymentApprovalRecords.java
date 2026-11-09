package io.github.gseobi.commerce.orchestration.payment.api;

import java.math.BigDecimal;

/** Internal storage port. Only the verified approval coordinator may invoke the mutation. */
public interface TenantPaymentApprovalRecords {

    boolean isReady(long merchantId, long orderId, long paymentId, BigDecimal amount);

    void approveReady(long merchantId, long orderId, long paymentId, BigDecimal amount, String providerTransactionId);
}
