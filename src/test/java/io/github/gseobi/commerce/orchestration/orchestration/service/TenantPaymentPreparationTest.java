package io.github.gseobi.commerce.orchestration.orchestration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.audit.api.TenantOrderAuditRecorder;
import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderPaymentClaim;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderView;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentRecords;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TenantPaymentPreparationTest {

    private final TenantOrderPaymentClaim orders = mock(TenantOrderPaymentClaim.class);
    private final TenantPaymentRecords payments = mock(TenantPaymentRecords.class);
    private final TenantPaymentPreparationSteps steps = mock(TenantPaymentPreparationSteps.class);
    private final TenantOrderAuditRecorder audit = mock(TenantOrderAuditRecorder.class);
    private final TenantPaymentPreparation preparation = new TenantPaymentPreparation(orders, payments, steps, audit);

    @Test
    void recordsAmountFromTheClaimedOrder() {
        var amount = new BigDecimal("10.25");
        when(orders.claimPayment(1L, 2L)).thenReturn(new TenantOrderView(1L, 2L, "PAYMENT_PENDING", amount, "KRW"));
        when(payments.insertReady(1L, 2L, amount)).thenReturn(3L);

        assertThat(preparation.prepare(1L, 2L)).isEqualTo(3L);
        var writes = inOrder(payments, steps, audit);
        writes.verify(payments).insertReady(1L, 2L, amount);
        writes.verify(steps).recordSuccess(1L, 2L);
        writes.verify(audit).recordPaymentPrepared(1L, 2L);
    }

    @Test
    void rejectsBeforeCallingPaymentWhenOrderClaimFails() {
        when(orders.claimPayment(1L, 2L)).thenThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        assertThatThrownBy(() -> preparation.prepare(1L, 2L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(payments, steps, audit);
    }
}
