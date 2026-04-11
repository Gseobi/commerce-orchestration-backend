package io.github.gseobi.commerce.orchestration.orchestration.api;

import io.github.gseobi.commerce.orchestration.orchestration.dto.response.OrderFlowResponse;

public interface OrderFlowUseCase {

    OrderFlowResponse orchestrate(Long orderId);

    OrderFlowResponse getOrderFlow(Long orderId);
}
