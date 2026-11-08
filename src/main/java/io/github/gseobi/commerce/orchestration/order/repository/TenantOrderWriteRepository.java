package io.github.gseobi.commerce.orchestration.order.repository;

import io.github.gseobi.commerce.orchestration.order.api.CreateTenantOrderCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenantOrderWriteRepository {

    private final JdbcClient jdbc;

    public long insert(long merchantId, CreateTenantOrderCommand command, String timezone, String recoveryPolicy) {
        return jdbc.sql("""
                insert into orders (ownership_mode, merchant_id, store_id, store_timezone,
                    store_recovery_policy, customer_id, total_amount, currency, status, created_at, updated_at)
                values ('TENANT', :merchantId, :storeId, :timezone, :recoveryPolicy, :customerId, :amount,
                    :currency, 'CREATED', statement_timestamp(), statement_timestamp())
                returning id
                """)
                .param("merchantId", merchantId)
                .param("storeId", command.storeId())
                .param("timezone", timezone)
                .param("recoveryPolicy", recoveryPolicy)
                .param("customerId", command.customerId())
                .param("amount", command.totalAmount())
                .param("currency", command.currency())
                .query(Long.class)
                .single();
    }

    public int cancelCreated(long merchantId, long orderId) {
        return jdbc.sql("""
                update orders set status = 'CANCELLED', updated_at = statement_timestamp()
                where ownership_mode = 'TENANT' and merchant_id = :merchantId and id = :orderId
                    and store_id is not null and status = 'CREATED'
                """)
                .param("merchantId", merchantId)
                .param("orderId", orderId)
                .update();
    }
}
