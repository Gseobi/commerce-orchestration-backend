package io.github.gseobi.commerce.orchestration.order.api;

import io.github.gseobi.commerce.orchestration.orchestration.dto.response.OrderFlowResponse;
import io.github.gseobi.commerce.orchestration.order.dto.request.CreateOrderRequest;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderDetailResponse;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderResponse;

public interface OrderFacade {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderDetailResponse getOrderDetail(Long orderId);

    OrderFlowResponse orchestrate(Long orderId);

    OrderFlowResponse getOrderFlow(Long orderId);
}
