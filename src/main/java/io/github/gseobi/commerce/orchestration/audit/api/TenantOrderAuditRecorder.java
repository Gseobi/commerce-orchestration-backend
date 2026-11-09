package io.github.gseobi.commerce.orchestration.audit.api;

/** Internal transaction participant for committed tenant order events. */
public interface TenantOrderAuditRecorder {

    void recordPaymentPrepared(long merchantId, long orderId);

    void recordPaymentApproved(long merchantId, long orderId);
}
