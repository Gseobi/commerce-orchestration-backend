package io.github.gseobi.commerce.orchestration.orchestration.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class TenantPaymentPreparationSteps {

    private final JdbcClient jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(long merchantId, long orderId) {
        jdbc.sql("""
                insert into orchestration_steps (ownership_mode, merchant_id, order_id, step_type,
                    status, created_at, updated_at)
                values ('TENANT', :merchantId, :orderId, 'PAYMENT_PREPARATION', 'SUCCESS',
                    statement_timestamp(), statement_timestamp())
                """)
                .param("merchantId", merchantId)
                .param("orderId", orderId)
                .update();
    }
}
