package io.github.gseobi.commerce.orchestration.payment.service;

import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentRecords;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class TenantPaymentRecordService implements TenantPaymentRecords {

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
}
