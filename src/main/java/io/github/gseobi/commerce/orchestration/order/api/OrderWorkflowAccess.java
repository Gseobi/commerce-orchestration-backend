package io.github.gseobi.commerce.orchestration.order.api;

public interface OrderWorkflowAccess {

    OrderExecutionView getOrderExecutionView(Long orderId);

    void markPaymentPending(Long orderId);

    void markPaid(Long orderId);

    void markSettlementRequested(Long orderId);

    void markNotificationRequested(Long orderId);

    void markCompleted(Long orderId);

    void markFailed(Long orderId);

    void markCancelled(Long orderId);
}
