package io.github.gseobi.commerce.orchestration.orchestration.service;

import io.github.gseobi.commerce.orchestration.audit.api.TenantOrderAuditRecorder;
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
    private final TenantPaymentPreparationSteps steps;
    private final TenantOrderAuditRecorder audit;

    @Transactional
    public long prepare(Long merchantId, Long orderId) {
        var claimed = orders.claimPayment(merchantId, orderId);
        long paymentId = payments.insertReady(claimed.merchantId(), claimed.orderId(), claimed.totalAmount());
        steps.recordSuccess(claimed.merchantId(), claimed.orderId());
        audit.recordPaymentPrepared(claimed.merchantId(), claimed.orderId());
        return paymentId;
    }
}
