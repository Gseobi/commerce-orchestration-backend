package io.github.gseobi.commerce.orchestration.order.api;

public interface OrderFlowUseCase {

    OrderFlowResponse orchestrate(Long orderId);

    OrderFlowResponse getOrderFlow(Long orderId);
}
