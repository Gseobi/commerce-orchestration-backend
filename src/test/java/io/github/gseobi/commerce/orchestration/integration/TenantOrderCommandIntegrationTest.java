package io.github.gseobi.commerce.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreRegistrationCommand;
import io.github.gseobi.commerce.orchestration.order.api.CreateTenantOrderCommand;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderCommands;
import io.github.gseobi.commerce.orchestration.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest
class TenantOrderCommandIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired private TenantOrderCommands commands;
    @Autowired private MerchantApplication merchants;
    @Autowired private OrderRepository legacyOrders;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void createsTenantOrderAndKeepsOriginalStoreSnapshot() {
        long merchant = merchant("A");
        long store = store(merchant);
        var order = commands.create(merchant, command(store));

        assertThat(order.merchantId()).isEqualTo(merchant);
        assertThat(order.status()).isEqualTo("CREATED");
        assertThat(order.totalAmount()).isEqualByComparingTo("10.25");
        assertThat(legacyOrders.findById(order.orderId())).isEmpty();
        jdbc.update("update stores set timezone = 'UTC' where id = ?", store);
        var row = jdbc.queryForMap("""
                select store_id, store_timezone, store_recovery_policy from orders where id = ?
                """, order.orderId());
        assertThat(row).containsEntry("store_id", store)
                .containsEntry("store_timezone", "Asia/Seoul")
                .containsEntry("store_recovery_policy", "MANUAL_REVIEW");
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isZero();
    }

    @Test
    void unavailableStoreDoesNotCreateAnOrder() {
        long first = merchant("A"), second = merchant("B");
        long store = store(first);
        assertError(() -> commands.create(second, command(store)), ErrorCode.STORE_NOT_FOUND);
        assertError(() -> commands.create(first, command(Long.MAX_VALUE)), ErrorCode.STORE_NOT_FOUND);
        jdbc.update("update stores set status = 'INACTIVE' where id = ?", store);
        assertError(() -> commands.create(first, command(store)), ErrorCode.STORE_NOT_FOUND);
        jdbc.update("update stores set status = 'ACTIVE' where id = ?", store);
        jdbc.update("update merchants set status = 'SUSPENDED' where id = ?", first);
        assertError(() -> commands.create(first, command(store)), ErrorCode.STORE_NOT_FOUND);
        assertThat(jdbc.queryForObject("select count(*) from orders", Long.class)).isZero();
    }

    @Test
    void orderCreationSharesTransactionAndLocksActiveMerchantAndStoreUntilCommit() {
        long merchant = merchant("A"), store = store(merchant);
        var outer = new TransactionTemplate(transactionManager);
        var concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(tx -> {
            commands.create(merchant, command(store));
            assertThatThrownBy(() -> concurrent.executeWithoutResult(ignored -> {
                jdbc.execute("set local lock_timeout = '250ms'");
                jdbc.update("update merchants set status = 'SUSPENDED' where id = ?", merchant);
            })).rootCause().isInstanceOfSatisfying(SQLException.class,
                    exception -> assertThat(exception.getSQLState()).isEqualTo("55P03"));
            assertThatThrownBy(() -> concurrent.executeWithoutResult(ignored -> {
                jdbc.execute("set local lock_timeout = '250ms'");
                jdbc.update("update stores set status = 'INACTIVE' where id = ?", store);
            })).rootCause().isInstanceOfSatisfying(SQLException.class,
                    exception -> assertThat(exception.getSQLState()).isEqualTo("55P03"));
            tx.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("select count(*) from orders", Long.class)).isZero();
        assertThat(jdbc.update("update stores set status = 'INACTIVE' where id = ?", store)).isOne();
    }

    @Test
    void cancellationIsScopedAndReplayDoesNotRewriteTheRow() {
        long first = merchant("A"), second = merchant("B");
        long order = commands.create(first, command(store(first))).orderId();
        long legacy = insertWithoutSnapshot(jdbc, "LEGACY", null);
        assertError(() -> commands.cancelCreated(second, order), ErrorCode.ORDER_NOT_FOUND);
        assertError(() -> commands.cancelCreated(first, legacy), ErrorCode.ORDER_NOT_FOUND);
        assertError(() -> commands.cancelCreated(first, Long.MAX_VALUE), ErrorCode.ORDER_NOT_FOUND);
        assertThat(commands.cancelCreated(first, order).status()).isEqualTo("CANCELLED");
        var cancelled = jdbc.queryForMap("select * from orders where id = ?", order);
        assertThat(commands.cancelCreated(first, order).status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForMap("select * from orders where id = ?", order)).isEqualTo(cancelled);
    }

    @Test
    void doesNotCancelOrderAfterPaymentStarts() {
        long owner = merchant("A");
        long order = commands.create(owner, command(store(owner))).orderId();
        jdbc.update("update orders set status = 'PAYMENT_PENDING' where id = ?", order);
        assertError(() -> commands.cancelCreated(owner, order), ErrorCode.INVALID_ORDER_STATE);
        assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, order))
                .isEqualTo("PAYMENT_PENDING");
    }

    @Test
    void databaseRejectsMissingCrossOwnerAndModifiedSnapshots() {
        long first = merchant("A"), second = merchant("B");
        long ownStore = store(first), foreignStore = store(second);
        assertThatThrownBy(() -> insertWithoutSnapshot(jdbc, "TENANT", first))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause()
                .hasMessageContaining("ck_orders_store_snapshot");
        assertThatThrownBy(() -> jdbc.update("""
                insert into orders (ownership_mode, merchant_id, store_id, store_timezone,
                    store_recovery_policy, customer_id, total_amount, currency, status, created_at, updated_at)
                values ('TENANT', ?, ?, 'UTC', 'MANUAL_REVIEW', 'fixture', 10, 'KRW', 'CREATED', now(), now())
                """, first, foreignStore)).isInstanceOf(DataIntegrityViolationException.class).rootCause()
                .hasMessageContaining("fk_orders_owner_store");
        long order = commands.create(first, command(ownStore)).orderId();
        assertThatThrownBy(() -> jdbc.update("""
                insert into orders (ownership_mode, store_id, store_timezone, store_recovery_policy,
                    customer_id, total_amount, currency, status, created_at, updated_at)
                values ('LEGACY', ?, 'UTC', 'MANUAL_REVIEW', 'fixture', 10, 'KRW', 'CREATED', now(), now())
                """, ownStore)).isInstanceOf(DataIntegrityViolationException.class).rootCause()
                .hasMessageContaining("ck_orders_store_snapshot");
        assertThatThrownBy(() -> jdbc.update("update orders set store_timezone = 'UTC' where id = ?", order))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause()
                .hasMessageContaining("Order store snapshot is immutable");
    }

    @Test
    void v9UpgradePreservesExistingTenantAndLegacyRowsWithoutInventingSnapshots() {
        String schema = "store_snapshot_" + UUID.randomUUID().toString().replace("-", "");
        var source = new DriverManagerDataSource(POSTGRESQL_CONTAINER.getJdbcUrl()
                + (POSTGRESQL_CONTAINER.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                POSTGRESQL_CONTAINER.getUsername(), POSTGRESQL_CONTAINER.getPassword());
        var isolated = new JdbcTemplate(source);
        try {
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                    .target("9").load().migrate();
            long owner = isolated.queryForObject("""
                    insert into merchants (code, name, status, timezone, default_recovery_policy, created_at, updated_at)
                    values ('A', 'Fixture', 'ACTIVE', 'UTC', 'MANUAL_REVIEW', now(), now()) returning id
                    """, Long.class);
            long historical = insertWithoutSnapshot(isolated, "TENANT", owner);
            insertWithoutSnapshot(isolated, "LEGACY", null);
            var before = isolated.queryForList("select to_jsonb(o)::text from orders o order by id", String.class);

            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();

            assertThat(isolated.queryForList("""
                    select (to_jsonb(o) - 'store_id' - 'store_timezone' - 'store_recovery_policy')::text
                    from orders o order by id
                    """, String.class)).isEqualTo(before);
            assertThat(isolated.queryForObject("select store_id from orders where id = ?", Long.class, historical))
                    .isNull();
            assertThatThrownBy(() -> insertWithoutSnapshot(isolated, "TENANT", owner))
                    .isInstanceOf(DataIntegrityViolationException.class).rootCause()
                    .hasMessageContaining("ck_orders_store_snapshot");
            assertThatThrownBy(() -> isolated.update("update orders set status = 'CANCELLED' where id = ?", historical))
                    .isInstanceOf(DataIntegrityViolationException.class).rootCause()
                    .hasMessageContaining("ck_orders_store_snapshot");
            assertThat(insertWithoutSnapshot(isolated, "LEGACY", null)).isPositive();
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private long insertWithoutSnapshot(JdbcTemplate db, String mode, Long merchant) {
        return db.queryForObject("""
                insert into orders (ownership_mode, merchant_id, customer_id, total_amount, currency,
                    status, created_at, updated_at)
                values (?, ?, 'fixture', 10, 'KRW', 'CREATED', now(), now()) returning id
                """, Long.class, mode, merchant);
    }

    private void assertError(Runnable action, ErrorCode error) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(error);
    }

    private long merchant(String code) {
        return merchants.registerMerchant(new MerchantRegistrationCommand(code, code, "Asia/Seoul")).id();
    }

    private long store(long merchant) {
        return merchants.registerStore(new StoreRegistrationCommand(merchant, "STORE", "Store")).id();
    }

    private CreateTenantOrderCommand command(long store) {
        return new CreateTenantOrderCommand(store, "test-customer", new BigDecimal("10.25"), "KRW");
    }
}
