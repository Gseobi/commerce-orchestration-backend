package io.github.gseobi.commerce.orchestration.order.dto.response;

import io.github.gseobi.commerce.orchestration.order.entity.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long orderId,
        String customerId,
        BigDecimal totalAmount,
        String currency,
        String description,
        String orderStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> paymentStatuses,
        List<String> settlementStatuses,
        List<String> notificationStatuses
) {

    public static OrderDetailResponse of(
            Order order,
            List<String> paymentStatuses,
            List<String> settlementStatuses,
            List<String> notificationStatuses
    ) {
        return new OrderDetailResponse(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getDescription(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                paymentStatuses,
                settlementStatuses,
                notificationStatuses
        );
    }
}
