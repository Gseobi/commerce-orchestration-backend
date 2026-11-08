package io.github.gseobi.commerce.orchestration.orchestration.service;

import io.github.gseobi.commerce.orchestration.order.api.TenantOrderPaymentClaim;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentRecords;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class TenantPaymentPreparation {

    private final TenantOrderPaymentClaim orders;
    private final TenantPaymentRecords payments;

    @Transactional
    public long prepare(Long merchantId, Long orderId) {
        var claimed = orders.claimPayment(merchantId, orderId);
        return payments.insertReady(claimed.merchantId(), claimed.orderId(), claimed.totalAmount());
    }
}
