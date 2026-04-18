package io.github.gseobi.commerce.orchestration.orchestration.service;

import io.github.gseobi.commerce.orchestration.audit.api.AuditRecorder;
import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.infrastructure.kafka.KafkaTopicNames;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationApplication;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationFailureView;
import io.github.gseobi.commerce.orchestration.orchestration.entity.OrchestrationStep;
import io.github.gseobi.commerce.orchestration.orchestration.entity.OrchestrationStepStatus;
import io.github.gseobi.commerce.orchestration.orchestration.entity.OrchestrationStepType;
import io.github.gseobi.commerce.orchestration.orchestration.repository.OrchestrationStepRepository;
import io.github.gseobi.commerce.orchestration.order.api.OrderExecutionView;
import io.github.gseobi.commerce.orchestration.order.api.OrderFlowResponse;
import io.github.gseobi.commerce.orchestration.order.api.OrderFlowUseCase;
import io.github.gseobi.commerce.orchestration.order.api.OrderWorkflowAccess;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventSummary;
import io.github.gseobi.commerce.orchestration.payment.api.PaymentApplication;
import io.github.gseobi.commerce.orchestration.payment.api.PaymentResponse;
import io.github.gseobi.commerce.orchestration.settlement.api.SettlementApplication;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CommerceOrchestrationService implements OrderFlowUseCase {

    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_NOTIFICATION_REQUESTED = "NOTIFICATION_REQUESTED";
    private static final String POLICY_AUTO_RETRY = "AUTO_RETRY";
    private static final String POLICY_MANUAL_INTERVENTION = "MANUAL_INTERVENTION";

    private final OrderWorkflowAccess orderWorkflowAccess;
    private final PaymentApplication paymentApplication;
    private final SettlementApplication settlementApplication;
    private final NotificationApplication notificationApplication;
    private final AuditRecorder auditRecorder;
    private final OutboxApplication outboxApplication;
    private final OrchestrationStepRepository orchestrationStepRepository;

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public OrderFlowResponse orchestrate(Long orderId) {
        OrderExecutionView order = orderWorkflowAccess.getOrderExecutionView(orderId);

        if (STATUS_COMPLETED.equals(order.status())
                || STATUS_CANCELLED.equals(order.status())
                || STATUS_NOTIFICATION_REQUESTED.equals(order.status())) {
            auditRecorder.record(orderId, "ORCHESTRATION_IDEMPOTENT_REPLAY",
                    "Existing state reused without re-processing");
            return getOrderFlow(orderId);
        }

        if (!STATUS_CREATED.equals(order.status())) {
            auditRecorder.record(orderId, "ORCHESTRATION_REJECTED",
                    "Invalid state for orchestration: " + order.status());
            return getOrderFlow(orderId);
        }

        recordStep(orderId, OrchestrationStepType.ORDER_CREATE, OrchestrationStepStatus.SUCCESS, "Order already created");

        try {
            orderWorkflowAccess.markPaymentPending(orderId);
            recordStep(orderId, OrchestrationStepType.PAYMENT, OrchestrationStepStatus.READY, "Payment requested");
            PaymentResponse paymentResponse = paymentApplication.approve(
                    orderId,
                    order.totalAmount(),
                    order.description()
            );
            orderWorkflowAccess.markPaid(orderId);
            recordStep(orderId, OrchestrationStepType.PAYMENT, OrchestrationStepStatus.SUCCESS,
                    "Payment approved: paymentId=" + paymentResponse.paymentId());

            Long settlementId = settlementApplication.request(orderId, order.description());
            orderWorkflowAccess.markSettlementRequested(orderId);
            recordStep(orderId, OrchestrationStepType.SETTLEMENT, OrchestrationStepStatus.SUCCESS,
                    "Settlement requested: settlementId=" + settlementId);
            createOutboxEvent(orderId, KafkaTopicNames.SETTLEMENT_REQUESTED, "SETTLEMENT_REQUESTED");

            Long notificationEventId = notificationApplication.request(orderId, order.description());
            orderWorkflowAccess.markNotificationRequested(orderId);
            recordStep(orderId, OrchestrationStepType.NOTIFICATION, OrchestrationStepStatus.SUCCESS,
                    "Notification requested: notificationEventId=" + notificationEventId);
            createOutboxEvent(orderId, KafkaTopicNames.NOTIFICATION_REQUESTED, "NOTIFICATION_REQUESTED");

            orderWorkflowAccess.markCompleted(orderId);
            recordStep(orderId, OrchestrationStepType.COMPLETE, OrchestrationStepStatus.SUCCESS,
                    "Happy path orchestration completed");
            auditRecorder.record(orderId, "ORDER_ORCHESTRATED", "Happy path orchestration completed");
        } catch (BusinessException exception) {
            handleFailure(orderId, exception);
        }

        return getOrderFlow(orderId);
    }

    @Override
    public OrderFlowResponse getOrderFlow(Long orderId) {
        OrderExecutionView order = orderWorkflowAccess.getOrderExecutionView(orderId);
        List<OrchestrationStep> steps = orchestrationStepRepository.findAllByOrderIdOrderByIdAsc(orderId);
        List<OutboxEventSummary> outboxEvents = outboxApplication.getOrderEvents(orderId);
        List<OrderFlowResponse.StepResponse> stepResponses = steps.stream()
                .map(step -> new OrderFlowResponse.StepResponse(
                        step.getId(),
                        step.getStepType().name(),
                        step.getStatus().name(),
                        step.getDetail(),
                        step.getCreatedAt()
                ))
                .toList();
        List<OrderFlowResponse.OutboxResponse> outboxResponses = outboxEvents.stream()
                .map(event -> new OrderFlowResponse.OutboxResponse(
                        event.outboxEventId(),
                        event.topic(),
                        event.eventType(),
                        event.status(),
                        event.payload(),
                        event.retryCount(),
                        event.nextAttemptAt(),
                        event.createdAt(),
                        event.lastAttemptAt(),
                        event.publishedAt(),
                        event.deadLetteredAt(),
                        event.failureCode(),
                        event.failureReason()
                ))
                .toList();
        return OrderFlowResponse.of(orderId, order.status(), stepResponses, outboxResponses);
    }

    private void recordStep(
            Long orderId,
            OrchestrationStepType stepType,
            OrchestrationStepStatus status,
            String detail
    ) {
        orchestrationStepRepository.save(new OrchestrationStep(orderId, stepType, status, detail));
    }

    private void createOutboxEvent(Long orderId, String topic, String eventType) {
        String payload = "{\"orderId\":" + orderId + ",\"eventType\":\"" + eventType + "\"}";
        outboxApplication.appendOrderEvent(orderId, topic, eventType, payload);
    }

    private void handleFailure(Long orderId, BusinessException exception) {
        if (exception.getErrorCode().name().startsWith("PAYMENT")) {
            handlePaymentFailure(orderId, exception);
            return;
        }

        if (exception.getErrorCode().name().startsWith("SETTLEMENT")) {
            handleSettlementFailure(orderId, exception);
            return;
        }

        if (exception.getErrorCode().name().startsWith("NOTIFICATION")) {
            handleNotificationFailure(orderId, exception);
        }
    }

    private void handlePaymentFailure(Long orderId, BusinessException exception) {
        orderWorkflowAccess.markFailed(orderId);
        recordStep(orderId, OrchestrationStepType.PAYMENT, OrchestrationStepStatus.FAILED, exception.getMessage());
        auditRecorder.record(orderId, "PAYMENT_FAILED", exception.getMessage());
    }

    private void handleSettlementFailure(Long orderId, BusinessException exception) {
        orderWorkflowAccess.markFailed(orderId);
        recordStep(orderId, OrchestrationStepType.SETTLEMENT, OrchestrationStepStatus.FAILED, exception.getMessage());
        PaymentResponse compensationResult = paymentApplication.cancelLatestApprovedPayment(
                orderId,
                "Settlement failure compensation"
        );
        orderWorkflowAccess.markCancelled(orderId);
        recordStep(orderId, OrchestrationStepType.COMPENSATION, OrchestrationStepStatus.SUCCESS,
                "Payment cancelled after settlement failure: paymentId=" + compensationResult.paymentId());
        auditRecorder.record(orderId, "SETTLEMENT_FAILED_COMPENSATED", exception.getMessage());
    }

    private void handleNotificationFailure(Long orderId, BusinessException exception) {
        orderWorkflowAccess.markFailed(orderId);
        recordStep(orderId, OrchestrationStepType.NOTIFICATION, OrchestrationStepStatus.FAILED, exception.getMessage());
        NotificationFailureView notificationFailure = notificationApplication.getLatestNotificationFailure(orderId)
                .orElse(null);

        if (notificationFailure != null && POLICY_AUTO_RETRY.equals(notificationFailure.handlingPolicy())) {
            recordStep(orderId, OrchestrationStepType.COMPENSATION, OrchestrationStepStatus.READY,
                    "Notification retry scheduled; payment and settlement are kept as-is, nextAttemptAt="
                            + notificationFailure.nextAttemptAt());
            auditRecorder.record(orderId, "NOTIFICATION_FAILED_RETRY_SCHEDULED",
                    exception.getMessage()
                            + ", notificationEventId=" + notificationFailure.notificationEventId()
                            + ", retryCount=" + notificationFailure.retryCount()
                            + ", nextAttemptAt=" + notificationFailure.nextAttemptAt());
            return;
        }

        if (notificationFailure != null && POLICY_MANUAL_INTERVENTION.equals(notificationFailure.handlingPolicy())) {
            recordStep(orderId, OrchestrationStepType.COMPENSATION, OrchestrationStepStatus.READY,
                    "Notification requires manual intervention; payment and settlement are kept as-is");
            auditRecorder.record(orderId, "NOTIFICATION_FAILED_MANUAL_INTERVENTION_REQUIRED",
                    exception.getMessage()
                            + ", notificationEventId=" + notificationFailure.notificationEventId()
                            + ", status=" + notificationFailure.status());
            return;
        }

        recordStep(orderId, OrchestrationStepType.COMPENSATION, OrchestrationStepStatus.READY,
                "Notification recovery pending; payment and settlement are kept as-is");
        auditRecorder.record(orderId, "NOTIFICATION_FAILED_RECOVERY_PENDING", exception.getMessage());
    }
}
