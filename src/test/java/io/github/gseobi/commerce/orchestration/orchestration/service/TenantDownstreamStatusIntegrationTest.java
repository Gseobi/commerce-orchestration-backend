package io.github.gseobi.commerce.orchestration.orchestration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.integration.TestcontainersIntegrationSupport;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreRegistrationCommand;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationApplication;
import io.github.gseobi.commerce.orchestration.notification.api.TenantNotificationStatusQuery;
import io.github.gseobi.commerce.orchestration.order.api.CreateTenantOrderCommand;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderCommands;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.settlement.api.SettlementApplication;
import io.github.gseobi.commerce.orchestration.settlement.api.TenantSettlementStatusQuery;
import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest
class TenantDownstreamStatusIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired private TenantDownstreamStatusQuery query;
    @Autowired private TenantSettlementStatusQuery settlements;
    @Autowired private TenantNotificationStatusQuery notifications;
    @Autowired private SettlementApplication legacySettlements;
    @Autowired private NotificationApplication legacyNotifications;
    @Autowired private TenantOrderCommands orders;
    @Autowired private TenantOrderQuery orderQuery;
    @Autowired private TenantPaymentPreparation preparation;
    @Autowired private MerchantApplication merchants;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void returnsOnlyLatestStatusesForTheOwnedOrder() {
        var first = order("A");
        var second = order("B");
        seedSettlement(first, "REQUESTED");
        seedSettlement(first, "COMPLETED");
        seedSettlement(second, "FAILED");
        seedNotification(first, "REQUESTED");
        seedNotification(first, "SENT");
        seedNotification(second, "RETRY_SCHEDULED");

        var result = query.getStatuses(first.merchantId(), first.orderId());

        assertThat(result.settlementStatus()).contains("COMPLETED");
        assertThat(result.notificationStatus()).contains("SENT");
        assertThat(settlements.findLatestStatus(second.merchantId(), first.orderId())).isEmpty();
        assertThat(notifications.findLatestStatus(second.merchantId(), first.orderId())).isEmpty();
        assertThat(legacySettlements.getSettlementStatuses(first.orderId())).isEmpty();
        assertThat(legacyNotifications.getNotificationStatuses(first.orderId())).isEmpty();
        assertThat(orderQuery.getOrder(first.merchantId(), first.orderId()).status()).isEqualTo("CREATED");
    }

    @Test
    void refusesForeignMissingAndLegacyOrdersWithoutFallingBackToLegacyRows() {
        var first = order("A");
        var second = order("B");
        long legacy = jdbc.queryForObject("""
                insert into orders (customer_id, total_amount, currency, status, created_at, updated_at)
                values ('fixture', 10, 'KRW', 'CREATED', now(), now()) returning id
                """, Long.class);
        jdbc.update("insert into settlements (order_id, status, created_at, updated_at) values (?, 'REQUESTED', now(), now())",
                legacy);
        jdbc.update("""
                insert into notification_events (order_id, status, created_at, updated_at)
                values (?, 'REQUESTED', now(), now())
                """, legacy);

        assertNotFound(second.merchantId(), first.orderId());
        assertNotFound(first.merchantId(), Long.MAX_VALUE);
        assertNotFound(first.merchantId(), legacy);
        assertThat(settlements.findLatestStatus(first.merchantId(), legacy)).isEmpty();
        assertThat(notifications.findLatestStatus(first.merchantId(), legacy)).isEmpty();
        assertThat(legacySettlements.getSettlementStatuses(legacy)).containsExactly("REQUESTED");
        assertThat(legacyNotifications.getNotificationStatuses(legacy)).containsExactly("REQUESTED");
    }

    @Test
    void readyPaymentDoesNotCreateDownstreamRecords() {
        var order = order("A");
        preparation.prepare(order.merchantId(), order.orderId());

        var result = query.getStatuses(order.merchantId(), order.orderId());

        assertThat(result.settlementStatus()).isEmpty();
        assertThat(result.notificationStatus()).isEmpty();
        assertThat(orderQuery.getOrder(order.merchantId(), order.orderId()).status()).isEqualTo("PAYMENT_PENDING");
        assertThat(jdbc.queryForObject("select count(*) from settlements", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification_events", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isZero();
    }

    private OwnedOrder order(String code) {
        long merchant = merchants.registerMerchant(new MerchantRegistrationCommand(code, code, "UTC")).id();
        long store = merchants.registerStore(new StoreRegistrationCommand(merchant, "STORE", "Store")).id();
        long order = orders.create(merchant, new CreateTenantOrderCommand(store, "fixture", BigDecimal.TEN, "KRW"))
                .orderId();
        return new OwnedOrder(merchant, order);
    }

    private void seedSettlement(OwnedOrder order, String status) {
        jdbc.update("""
                insert into settlements (ownership_mode, merchant_id, order_id, status, created_at, updated_at)
                values ('TENANT', ?, ?, ?, now(), now())
                """, order.merchantId(), order.orderId(), status);
    }

    private void seedNotification(OwnedOrder order, String status) {
        jdbc.update("""
                insert into notification_events (ownership_mode, merchant_id, order_id, status, created_at, updated_at)
                values ('TENANT', ?, ?, ?, now(), now())
                """, order.merchantId(), order.orderId(), status);
    }

    private void assertNotFound(long merchant, long order) {
        assertThatThrownBy(() -> query.getStatuses(merchant, order)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    private record OwnedOrder(long merchantId, long orderId) {
    }
}
