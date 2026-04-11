package io.github.gseobi.commerce.orchestration.orchestration.dto.response;

import io.github.gseobi.commerce.orchestration.orchestration.entity.OrchestrationStep;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventSummary;
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
            List<OrchestrationStep> steps,
            List<OutboxEventSummary> outboxEvents
    ) {
        return new OrderFlowResponse(
                orderId,
                orderStatus,
                steps.stream()
                        .map(step -> new StepResponse(
                                step.getId(),
                                step.getStepType().name(),
                                step.getStatus().name(),
                                step.getDetail(),
                                step.getCreatedAt()))
                        .toList(),
                outboxEvents.stream()
                        .map(event -> new OutboxResponse(
                                event.outboxEventId(),
                                event.topic(),
                                event.eventType(),
                                event.status(),
                                event.payload(),
                                event.createdAt(),
                                event.publishedAt(),
                                event.failureReason()))
                        .toList()
        );
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
            LocalDateTime createdAt,
            LocalDateTime publishedAt,
            String failureReason
    ) {
    }
}
