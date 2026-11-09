package io.github.gseobi.commerce.orchestration.order.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.OrderStoreAccess;
import io.github.gseobi.commerce.orchestration.order.api.CreateTenantOrderCommand;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderCommands;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderPaymentClaim;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderPaymentTransition;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderView;
import io.github.gseobi.commerce.orchestration.order.entity.OrderStatus;
import io.github.gseobi.commerce.orchestration.order.repository.TenantOrderWriteRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class TenantOrderCommandService implements TenantOrderCommands, TenantOrderPaymentClaim, TenantOrderPaymentTransition {

    private final OrderStoreAccess stores;
    private final TenantOrderWriteRepository orders;
    private final TenantOrderQuery query;

    @Override
    public TenantOrderView create(Long merchantId, CreateTenantOrderCommand command) {
        requirePositive(merchantId);
        if (command == null) {
            throw new IllegalArgumentException("Order command is required.");
        }
        requirePositive(command.storeId());
        String customerId = command.customerId();
        if (customerId == null || customerId.isBlank() || customerId.length() > 100 || customerId.indexOf(0) >= 0) {
            throw new IllegalArgumentException("Invalid customer identifier.");
        }
        BigDecimal amount = command.totalAmount();
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || (long) amount.precision() - amount.scale() > 16) {
            throw new IllegalArgumentException("Amount must fit a positive numeric(18,2) without rounding.");
        }
        if (command.currency() == null || !command.currency().matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter uppercase code.");
        }

        var store = stores.lockActiveStore(merchantId, command.storeId());
        long orderId = orders.insert(merchantId, command, store.timezone(), store.recoveryPolicy());
        return query.getOrder(merchantId, orderId);
    }

    @Override
    public TenantOrderView cancelCreated(Long merchantId, Long orderId) {
        requirePositive(merchantId);
        requirePositive(orderId);
        orders.cancelCreated(merchantId, orderId);
        TenantOrderView order = query.getOrder(merchantId, orderId);
        if (!OrderStatus.CANCELLED.name().equals(order.status())) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
        return order;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public TenantOrderView claimPayment(Long merchantId, Long orderId) {
        requirePositive(merchantId);
        requirePositive(orderId);
        int updated = orders.claimPayment(merchantId, orderId);
        TenantOrderView order = query.getOrder(merchantId, orderId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
        return order;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markPaid(long merchantId, long orderId, BigDecimal amount, String currency) {
        if (orders.markPaid(merchantId, orderId, amount, currency) != 1) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
    }

    private void requirePositive(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Positive identifiers are required.");
        }
    }
}
