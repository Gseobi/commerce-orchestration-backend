package io.github.gseobi.commerce.orchestration.order.api;

/** Internal only. The caller must authorize WRITE access for the merchant before invoking this port. */
public interface TenantOrderCommands {

    TenantOrderView create(Long merchantId, CreateTenantOrderCommand command);

    TenantOrderView cancelCreated(Long merchantId, Long orderId);
}
