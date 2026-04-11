package io.github.gseobi.commerce.orchestration.order.controller;

import io.github.gseobi.commerce.orchestration.common.api.ApiResponse;
import io.github.gseobi.commerce.orchestration.orchestration.dto.response.OrderFlowResponse;
import io.github.gseobi.commerce.orchestration.orchestration.service.CommerceOrchestrationService;
import io.github.gseobi.commerce.orchestration.order.dto.request.CreateOrderRequest;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderDetailResponse;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderResponse;
import io.github.gseobi.commerce.orchestration.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CommerceOrchestrationService commerceOrchestrationService;

    public OrderController(
            OrderService orderService,
            CommerceOrchestrationService commerceOrchestrationService
    ) {
        this.orderService = orderService;
        this.commerceOrchestrationService = commerceOrchestrationService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderService.createOrder(request));
    }

    @PostMapping("/{orderId}/orchestrate")
    public ApiResponse<OrderFlowResponse> orchestrate(@PathVariable Long orderId) {
        return ApiResponse.success(commerceOrchestrationService.orchestrate(orderId));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.getOrderDetail(orderId));
    }

    @GetMapping("/{orderId}/flow")
    public ApiResponse<OrderFlowResponse> getOrderFlow(@PathVariable Long orderId) {
        return ApiResponse.success(commerceOrchestrationService.getOrderFlow(orderId));
    }
}
