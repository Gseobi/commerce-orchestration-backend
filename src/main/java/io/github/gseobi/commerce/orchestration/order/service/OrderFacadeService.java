package io.github.gseobi.commerce.orchestration.order.service;

import io.github.gseobi.commerce.orchestration.orchestration.api.OrderFlowUseCase;
import io.github.gseobi.commerce.orchestration.orchestration.dto.response.OrderFlowResponse;
import io.github.gseobi.commerce.orchestration.order.api.OrderFacade;
import io.github.gseobi.commerce.orchestration.order.dto.request.CreateOrderRequest;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderDetailResponse;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class OrderFacadeService implements OrderFacade {

    private final OrderService orderService;
    private final OrderFlowUseCase orderFlowUseCase;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @Override
    public OrderDetailResponse getOrderDetail(Long orderId) {
        return orderService.getOrderDetail(orderId);
    }

    @Override
    public OrderFlowResponse orchestrate(Long orderId) {
        return orderFlowUseCase.orchestrate(orderId);
    }

    @Override
    public OrderFlowResponse getOrderFlow(Long orderId) {
        return orderFlowUseCase.getOrderFlow(orderId);
    }
}
