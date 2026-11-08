package io.github.gseobi.commerce.orchestration.orchestration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.notification.api.TenantNotificationStatusQuery;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderView;
import io.github.gseobi.commerce.orchestration.settlement.api.TenantSettlementStatusQuery;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantDownstreamStatusQueryTest {

    private final TenantOrderQuery orders = mock(TenantOrderQuery.class);
    private final TenantSettlementStatusQuery settlements = mock(TenantSettlementStatusQuery.class);
    private final TenantNotificationStatusQuery notifications = mock(TenantNotificationStatusQuery.class);
    private final TenantDownstreamStatusQuery query = new TenantDownstreamStatusQuery(orders, settlements, notifications);

    @Test
    void checksTheOrderFirstAndKeepsAnAbsentChildDistinctFromStatus() {
        when(orders.getOrder(1L, 2L)).thenReturn(new TenantOrderView(1L, 2L, "PAID", BigDecimal.TEN, "KRW"));
        when(settlements.findLatestStatus(1L, 2L)).thenReturn(Optional.of("REQUESTED"));
        when(notifications.findLatestStatus(1L, 2L)).thenReturn(Optional.empty());

        var result = query.getStatuses(1L, 2L);

        assertThat(result.settlementStatus()).contains("REQUESTED");
        assertThat(result.notificationStatus()).isEmpty();
        var calls = inOrder(orders, settlements, notifications);
        calls.verify(orders).getOrder(1L, 2L);
        calls.verify(settlements).findLatestStatus(1L, 2L);
        calls.verify(notifications).findLatestStatus(1L, 2L);
    }

    @Test
    void neverQueriesChildrenWhenParentIsNotVisible() {
        when(orders.getOrder(1L, 2L)).thenThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        assertThatThrownBy(() -> query.getStatuses(1L, 2L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        verifyNoInteractions(settlements, notifications);
    }
}
