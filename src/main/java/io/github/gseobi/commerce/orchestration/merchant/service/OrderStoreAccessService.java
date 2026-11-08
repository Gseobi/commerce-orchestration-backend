package io.github.gseobi.commerce.orchestration.merchant.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.OrderStoreAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class OrderStoreAccessService implements OrderStoreAccess {

    private final JdbcClient jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderStoreSnapshot lockActiveStore(long merchantId, long storeId) {
        return jdbc.sql("""
                select s.timezone, s.recovery_policy
                from stores s join merchants m on m.id = s.merchant_id
                where s.id = :storeId and m.id = :merchantId
                    and s.status = 'ACTIVE' and m.status = 'ACTIVE'
                for share of m, s
                """)
                .param("merchantId", merchantId)
                .param("storeId", storeId)
                .query((row, index) -> new OrderStoreSnapshot(row.getString("timezone"),
                        row.getString("recovery_policy")))
                .optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }
}
