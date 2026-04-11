package io.github.gseobi.commerce.orchestration.order.dto.response;

import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.payment.entity.Payment;
import io.github.gseobi.commerce.orchestration.settlement.entity.Settlement;
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
            List<Payment> payments,
            List<Settlement> settlements,
            List<NotificationEvent> notificationEvents
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
                payments.stream().map(payment -> payment.getStatus().name()).toList(),
                settlements.stream().map(settlement -> settlement.getStatus().name()).toList(),
                notificationEvents.stream().map(notification -> notification.getStatus().name()).toList()
        );
    }
}
