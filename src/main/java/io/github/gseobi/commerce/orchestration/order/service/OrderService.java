package io.github.gseobi.commerce.orchestration.order.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationApplication;
import io.github.gseobi.commerce.orchestration.order.api.OrderWorkflowAccess;
import io.github.gseobi.commerce.orchestration.order.api.OrderExecutionView;
import io.github.gseobi.commerce.orchestration.order.dto.request.CreateOrderRequest;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderDetailResponse;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderResponse;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.order.entity.OrderStatus;
import io.github.gseobi.commerce.orchestration.order.repository.OrderRepository;
import io.github.gseobi.commerce.orchestration.payment.api.PaymentApplication;
import io.github.gseobi.commerce.orchestration.settlement.api.SettlementApplication;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class OrderService implements OrderWorkflowAccess {

    private final OrderRepository orderRepository;
    private final PaymentApplication paymentApplication;
    private final SettlementApplication settlementApplication;
    private final NotificationApplication notificationApplication;

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
        return OrderDetailResponse.of(
                order,
                paymentApplication.getPaymentStatuses(orderId),
                settlementApplication.getSettlementStatuses(orderId),
                notificationApplication.getNotificationStatuses(orderId)
        );
    }

    @Override
    public OrderExecutionView getOrderExecutionView(Long orderId) {
        Order order = getOrderEntity(orderId);
        return new OrderExecutionView(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getDescription()
        );
    }

    private Order getOrderEntity(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    @Transactional
    @Override
    public void markPaymentPending(Long orderId) {
        Order order = getOrderEntity(orderId);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);
    }

    @Transactional
    @Override
    public void markPaid(Long orderId) {
        Order order = getOrderEntity(orderId);
        order.transitionTo(OrderStatus.PAID);
    }

    @Transactional
    @Override
    public void markSettlementRequested(Long orderId) {
        Order order = getOrderEntity(orderId);
        order.transitionTo(OrderStatus.SETTLEMENT_REQUESTED);
    }

    @Transactional
    @Override
    public void markNotificationRequested(Long orderId) {
        Order order = getOrderEntity(orderId);
        order.transitionTo(OrderStatus.NOTIFICATION_REQUESTED);
    }

    @Transactional
    @Override
    public void markCompleted(Long orderId) {
        Order order = getOrderEntity(orderId);
        order.transitionTo(OrderStatus.COMPLETED);
    }

    @Transactional
    @Override
    public void markFailed(Long orderId) {
        Order order = getOrderEntity(orderId);
        if (order.getStatus() != OrderStatus.FAILED) {
            order.transitionTo(OrderStatus.FAILED);
        }
    }

    @Transactional
    @Override
    public void markCancelled(Long orderId) {
        Order order = getOrderEntity(orderId);
        if (order.getStatus() == OrderStatus.FAILED) {
            order.transitionTo(OrderStatus.CANCELLED);
            return;
        }
        if (order.getStatus() != OrderStatus.CANCELLED) {
            order.transitionTo(OrderStatus.CANCELLED);
        }
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
