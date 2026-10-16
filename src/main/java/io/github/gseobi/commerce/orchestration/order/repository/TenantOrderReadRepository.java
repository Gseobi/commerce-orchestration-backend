package io.github.gseobi.commerce.orchestration.order.repository;

import io.github.gseobi.commerce.orchestration.order.entity.Order;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TenantOrderReadRepository extends Repository<Order, Long> {

    // Native scalar projection intentionally avoids the LEGACY-only entity mapping.
    @Query(value = """
            select merchant_id as "merchantId", id as "orderId", status,
                   total_amount as "totalAmount", currency
            from orders
            where ownership_mode = 'TENANT' and merchant_id = :merchantId and id = :orderId
            """, nativeQuery = true)
    Optional<Snapshot> findOwnedOrder(@Param("merchantId") Long merchantId, @Param("orderId") Long orderId);

    interface Snapshot {
        Long getMerchantId();
        Long getOrderId();
        String getStatus();
        BigDecimal getTotalAmount();
        String getCurrency();
    }
}
