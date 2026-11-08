package io.github.gseobi.commerce.orchestration.notification.service;

import io.github.gseobi.commerce.orchestration.notification.api.TenantNotificationStatusQuery;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TenantNotificationStatusService implements TenantNotificationStatusQuery {

    private final JdbcClient jdbc;

    @Override
    public Optional<String> findLatestStatus(long merchantId, long orderId) {
        return jdbc.sql("""
                select status from notification_events
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
