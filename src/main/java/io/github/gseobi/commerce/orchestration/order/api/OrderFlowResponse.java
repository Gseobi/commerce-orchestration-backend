package io.github.gseobi.commerce.orchestration.order.api;
import java.time.LocalDateTime;
import java.util.List;

public record OrderFlowResponse(
        Long orderId,
        String orderStatus,
        List<StepResponse> steps,
        List<OutboxResponse> outboxEvents
) {

    public static OrderFlowResponse of(
            Long orderId,
            String orderStatus,
            List<StepResponse> steps,
            List<OutboxResponse> outboxEvents
    ) {
        return new OrderFlowResponse(orderId, orderStatus, steps, outboxEvents);
    }

    public record StepResponse(
            Long stepId,
            String stepType,
            String status,
            String detail,
            LocalDateTime createdAt
    ) {
    }

    public record OutboxResponse(
            Long outboxEventId,
            String topic,
            String eventType,
            String status,
            String payload,
            int retryCount,
            LocalDateTime nextAttemptAt,
            LocalDateTime createdAt,
            LocalDateTime lastAttemptAt,
            LocalDateTime publishedAt,
            LocalDateTime deadLetteredAt,
            String failureCode,
            String failureReason
    ) {
    }
}
