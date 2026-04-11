package io.github.gseobi.commerce.orchestration.order.dto.response;

import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        String customerId,
        BigDecimal totalAmount,
        String currency,
        String description,
        OrderStatus status,
        LocalDateTime createdAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getDescription(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
