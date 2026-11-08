package io.github.gseobi.commerce.orchestration.audit.service;

import io.github.gseobi.commerce.orchestration.audit.api.TenantOrderAuditRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class TenantOrderAuditService implements TenantOrderAuditRecorder {

    private final JdbcClient jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaymentPrepared(long merchantId, long orderId) {
        jdbc.sql("""
                insert into audit_logs (ownership_mode, merchant_id, order_id, action, detail, created_at, updated_at)
                values ('TENANT', :merchantId, :orderId, 'TENANT_PAYMENT_PREPARED',
                    'Internal payment prepared; provider not called.', statement_timestamp(), statement_timestamp())
                """)
                .param("merchantId", merchantId)
                .param("orderId", orderId)
                .update();
    }
}
