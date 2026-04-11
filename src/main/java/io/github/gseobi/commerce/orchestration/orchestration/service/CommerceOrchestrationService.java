package io.github.gseobi.commerce.orchestration.orchestration.service;

import io.github.gseobi.commerce.orchestration.audit.entity.AuditLog;
import io.github.gseobi.commerce.orchestration.audit.repository.AuditLogRepository;
import io.github.gseobi.commerce.orchestration.infrastructure.kafka.KafkaTopicNames;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.service.NotificationService;
import io.github.gseobi.commerce.orchestration.orchestration.dto.response.OrderFlowResponse;
import io.github.gseobi.commerce.orchestration.orchestration.entity.OrchestrationStep;
import io.github.gseobi.commerce.orchestration.orchestration.entity.OrchestrationStepStatus;
import io.github.gseobi.commerce.orchestration.orchestration.entity.OrchestrationStepType;
import io.github.gseobi.commerce.orchestration.orchestration.repository.OrchestrationStepRepository;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.order.entity.OrderStatus;
import io.github.gseobi.commerce.orchestration.order.service.OrderService;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import io.github.gseobi.commerce.orchestration.payment.dto.response.PaymentResponse;
import io.github.gseobi.commerce.orchestration.payment.service.PaymentService;
import io.github.gseobi.commerce.orchestration.settlement.entity.Settlement;
import io.github.gseobi.commerce.orchestration.settlement.service.SettlementService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommerceOrchestrationService {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final SettlementService settlementService;
    private final NotificationService notificationService;
    private final OrchestrationStepRepository orchestrationStepRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;

    public CommerceOrchestrationService(
            OrderService orderService,
            PaymentService paymentService,
            SettlementService settlementService,
            NotificationService notificationService,
            OrchestrationStepRepository orchestrationStepRepository,
            OutboxEventRepository outboxEventRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.settlementService = settlementService;
        this.notificationService = notificationService;
        this.orchestrationStepRepository = orchestrationStepRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public OrderFlowResponse orchestrate(Long orderId) {
        Order order = orderService.getOrderEntity(orderId);

        if (order.getStatus() != OrderStatus.CREATED) {
            return getOrderFlow(orderId);
        }

        recordStep(order, OrchestrationStepType.ORDER_CREATE, OrchestrationStepStatus.SUCCESS, "Order already created");

        orderService.markPaymentPending(order);
        recordStep(order, OrchestrationStepType.PAYMENT, OrchestrationStepStatus.READY, "Payment requested");
        PaymentResponse paymentResponse = paymentService.approve(order);
        orderService.markPaid(order);
        recordStep(order, OrchestrationStepType.PAYMENT, OrchestrationStepStatus.SUCCESS,
                "Payment approved: paymentId=" + paymentResponse.paymentId());

        Settlement settlement = settlementService.request(order);
        orderService.markSettlementRequested(order);
        recordStep(order, OrchestrationStepType.SETTLEMENT, OrchestrationStepStatus.SUCCESS,
                "Settlement requested: settlementId=" + settlement.getId());
        createOutboxEvent(order, KafkaTopicNames.SETTLEMENT_REQUESTED, "SETTLEMENT_REQUESTED");

        NotificationEvent notificationEvent = notificationService.request(order);
        orderService.markNotificationRequested(order);
        recordStep(order, OrchestrationStepType.NOTIFICATION, OrchestrationStepStatus.SUCCESS,
                "Notification requested: notificationEventId=" + notificationEvent.getId());
        createOutboxEvent(order, KafkaTopicNames.NOTIFICATION_REQUESTED, "NOTIFICATION_REQUESTED");

        recordStep(order, OrchestrationStepType.COMPLETE, OrchestrationStepStatus.SUCCESS,
                "Happy path orchestration scaffold completed");
        auditLogRepository.save(new AuditLog(order, "ORDER_ORCHESTRATED", "Initial happy path orchestration completed"));

        // TODO retry/backoff, idempotency, compensation, actual Kafka publish 연동
        return getOrderFlow(orderId);
    }

    public OrderFlowResponse getOrderFlow(Long orderId) {
        Order order = orderService.getOrderEntity(orderId);
        List<OrchestrationStep> steps = orchestrationStepRepository.findAllByOrderIdOrderByIdAsc(orderId);
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAllByOrderIdOrderByIdAsc(orderId);
        return OrderFlowResponse.of(orderId, order.getStatus().name(), steps, outboxEvents);
    }

    private void recordStep(
            Order order,
            OrchestrationStepType stepType,
            OrchestrationStepStatus status,
            String detail
    ) {
        orchestrationStepRepository.save(new OrchestrationStep(order, stepType, status, detail));
    }

    private void createOutboxEvent(Order order, String topic, String eventType) {
        String payload = "{\"orderId\":" + order.getId() + ",\"eventType\":\"" + eventType + "\"}";
        outboxEventRepository.save(new OutboxEvent(order, topic, eventType, payload, OutboxStatus.READY));
    }
}
