package io.github.gseobi.commerce.orchestration.order.api;

/** Internal transaction participant; caller must authorize WRITE and persist the payment in the same transaction. */
public interface TenantOrderPaymentClaim {

    TenantOrderView claimPayment(Long merchantId, Long orderId);
}
