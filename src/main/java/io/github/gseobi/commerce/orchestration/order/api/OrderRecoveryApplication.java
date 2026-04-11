package io.github.gseobi.commerce.orchestration.order.api;

public interface OrderRecoveryApplication {

    void completeAfterNotificationRecovery(Long orderId);
}
