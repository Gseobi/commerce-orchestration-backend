package io.github.gseobi.commerce.orchestration.order.api;

/** Internal read port. Callers must authorize merchant access before invoking it. */
public interface TenantOrderQuery {

    TenantOrderView getOrder(Long merchantId, Long orderId);
}
