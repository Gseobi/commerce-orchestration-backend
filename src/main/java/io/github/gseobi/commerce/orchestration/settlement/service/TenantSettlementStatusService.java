package io.github.gseobi.commerce.orchestration.settlement.service;

import io.github.gseobi.commerce.orchestration.settlement.api.TenantSettlementStatusQuery;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TenantSettlementStatusService implements TenantSettlementStatusQuery {

    private final JdbcClient jdbc;

    @Override
    public Optional<String> findLatestStatus(long merchantId, long orderId) {
        return jdbc.sql("""
                select status from settlements
                where ownership_mode = 'TENANT' and merchant_id = :merchantId and order_id = :orderId
                order by id desc
                limit 1
                """)
                .param("merchantId", merchantId)
                .param("orderId", orderId)
                .query(String.class)
                .optional();
    }
}
