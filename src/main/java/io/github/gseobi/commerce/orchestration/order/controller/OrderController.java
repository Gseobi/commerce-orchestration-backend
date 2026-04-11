package io.github.gseobi.commerce.orchestration.order.controller;

import io.github.gseobi.commerce.orchestration.common.api.ApiResponse;
import io.github.gseobi.commerce.orchestration.order.api.OrderFacade;
import io.github.gseobi.commerce.orchestration.order.dto.request.CreateOrderRequest;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderDetailResponse;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderResponse;
import io.github.gseobi.commerce.orchestration.orchestration.dto.response.OrderFlowResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderFacade.createOrder(request));
    }

    @PostMapping("/{orderId}/orchestrate")
    public ApiResponse<OrderFlowResponse> orchestrate(@PathVariable Long orderId) {
        return ApiResponse.success(orderFacade.orchestrate(orderId));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderFacade.getOrderDetail(orderId));
    }

    @GetMapping("/{orderId}/flow")
    public ApiResponse<OrderFlowResponse> getOrderFlow(@PathVariable Long orderId) {
        return ApiResponse.success(orderFacade.getOrderFlow(orderId));
    }
}
