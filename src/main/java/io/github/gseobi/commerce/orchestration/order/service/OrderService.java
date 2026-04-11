package io.github.gseobi.commerce.orchestration.order.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.repository.NotificationEventRepository;
import io.github.gseobi.commerce.orchestration.order.dto.request.CreateOrderRequest;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderDetailResponse;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderResponse;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.order.entity.OrderStatus;
import io.github.gseobi.commerce.orchestration.order.repository.OrderRepository;
import io.github.gseobi.commerce.orchestration.payment.entity.Payment;
import io.github.gseobi.commerce.orchestration.payment.repository.PaymentRepository;
import io.github.gseobi.commerce.orchestration.settlement.entity.Settlement;
import io.github.gseobi.commerce.orchestration.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final NotificationEventRepository notificationEventRepository;

    public OrderService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            SettlementRepository settlementRepository,
            NotificationEventRepository notificationEventRepository
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.settlementRepository = settlementRepository;
        this.notificationEventRepository = notificationEventRepository;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        validateCreateOrderRequest(request);
        Order order = new Order(
                request.customerId(),
                request.totalAmount(),
                request.currency(),
                request.description(),
                OrderStatus.CREATED
        );
        return OrderResponse.from(orderRepository.save(order));
    }

    public OrderDetailResponse getOrderDetail(Long orderId) {
        Order order = getOrderEntity(orderId);
        List<Payment> payments = paymentRepository.findAllByOrderId(orderId);
        List<Settlement> settlements = settlementRepository.findAllByOrderId(orderId);
        List<NotificationEvent> notificationEvents = notificationEventRepository.findAllByOrderId(orderId);
        return OrderDetailResponse.of(order, payments, settlements, notificationEvents);
    }

    public Order getOrderEntity(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    @Transactional
    public Order markPaymentPending(Order order) {
        order.changeStatus(OrderStatus.PAYMENT_PENDING);
        return order;
    }

    @Transactional
    public Order markPaid(Order order) {
        order.changeStatus(OrderStatus.PAID);
        return order;
    }

    @Transactional
    public Order markSettlementRequested(Order order) {
        order.changeStatus(OrderStatus.SETTLEMENT_REQUESTED);
        return order;
    }

    @Transactional
    public Order markNotificationRequested(Order order) {
        order.changeStatus(OrderStatus.NOTIFICATION_REQUESTED);
        return order;
    }

    private void validateCreateOrderRequest(CreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("주문 생성 요청이 비어 있습니다.");
        }
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("customerId는 필수입니다.");
        }
        if (request.totalAmount() == null || request.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("totalAmount는 0보다 커야 합니다.");
        }
        if (request.currency() == null || request.currency().isBlank()) {
            throw new IllegalArgumentException("currency는 필수입니다.");
        }
    }
}
