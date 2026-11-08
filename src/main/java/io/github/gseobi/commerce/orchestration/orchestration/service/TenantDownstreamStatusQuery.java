package io.github.gseobi.commerce.orchestration.orchestration.service;

import io.github.gseobi.commerce.orchestration.notification.api.TenantNotificationStatusQuery;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.settlement.api.TenantSettlementStatusQuery;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TenantDownstreamStatusQuery {

    private final TenantOrderQuery orders;
    private final TenantSettlementStatusQuery settlements;
    private final TenantNotificationStatusQuery notifications;

    public View getStatuses(Long merchantId, Long orderId) {
        var order = orders.getOrder(merchantId, orderId);
        return new View(settlements.findLatestStatus(order.merchantId(), order.orderId()),
                notifications.findLatestStatus(order.merchantId(), order.orderId()));
    }

    record View(Optional<String> settlementStatus, Optional<String> notificationStatus) {
    }
}
