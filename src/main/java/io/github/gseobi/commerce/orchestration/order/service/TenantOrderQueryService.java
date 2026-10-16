package io.github.gseobi.commerce.orchestration.order.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderView;
import io.github.gseobi.commerce.orchestration.order.repository.TenantOrderReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TenantOrderQueryService implements TenantOrderQuery {

    private final TenantOrderReadRepository repository;

    @Override
    public TenantOrderView getOrder(Long merchantId, Long orderId) {
        if (merchantId == null || merchantId <= 0 || orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Positive merchantId and orderId are required.");
        }
        return repository.findOwnedOrder(merchantId, orderId)
                .map(row -> new TenantOrderView(row.getMerchantId(), row.getOrderId(), row.getStatus(),
                        row.getTotalAmount(), row.getCurrency()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
