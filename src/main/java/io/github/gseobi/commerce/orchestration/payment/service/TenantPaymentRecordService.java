package io.github.gseobi.commerce.orchestration.payment.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentApprovalRecords;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentRecords;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class TenantPaymentRecordService implements TenantPaymentRecords, TenantPaymentApprovalRecords {

    private final JdbcClient jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long insertReady(long merchantId, long orderId, BigDecimal amount) {
        return jdbc.sql("""
                insert into payments (ownership_mode, merchant_id, order_id, amount,
                    status, created_at, updated_at)
                values ('TENANT', :merchantId, :orderId, :amount,
                    'READY', statement_timestamp(), statement_timestamp())
                returning id
                """)
                .param("merchantId", merchantId)
                .param("orderId", orderId)
                .param("amount", amount)
                .query(Long.class)
                .single();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReady(long merchantId, long orderId, long paymentId, BigDecimal amount) {
        return jdbc.sql("""
                select exists (select 1 from payments
                    where ownership_mode = 'TENANT' and merchant_id = :merchantId
                        and order_id = :orderId and id = :paymentId and amount = :amount
                        and status = 'READY' and provider_transaction_id is null)
                """)
                .param("merchantId", merchantId)
                .param("orderId", orderId)
                .param("paymentId", paymentId)
                .param("amount", amount)
                .query(Boolean.class)
                .single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void approveReady(long merchantId, long orderId, long paymentId, BigDecimal amount,
                             String providerTransactionId) {
        int updated = jdbc.sql("""
                update payments set status = 'APPROVED', provider_transaction_id = :reference,
                    updated_at = statement_timestamp(), version = version + 1
                where ownership_mode = 'TENANT' and merchant_id = :merchantId
                    and order_id = :orderId and id = :paymentId and amount = :amount
                    and status = 'READY' and provider_transaction_id is null
                """)
                .param("merchantId", merchantId)
                .param("orderId", orderId)
                .param("paymentId", paymentId)
                .param("amount", amount)
                .param("reference", providerTransactionId)
                .update();
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
    }
}
