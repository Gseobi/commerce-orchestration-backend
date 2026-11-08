package io.github.gseobi.commerce.orchestration.orchestration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.integration.TestcontainersIntegrationSupport;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreRegistrationCommand;
import io.github.gseobi.commerce.orchestration.order.api.CreateTenantOrderCommand;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderCommands;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderPaymentClaim;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentRecords;
import io.github.gseobi.commerce.orchestration.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest
class TenantPaymentPreparationIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired private TenantPaymentPreparation preparation;
    @Autowired private TenantOrderCommands commands;
    @Autowired private TenantOrderPaymentClaim claims;
    @Autowired private TenantOrderQuery query;
    @Autowired private TenantPaymentRecords payments;
    @Autowired private PaymentRepository legacyPayments;
    @Autowired private MerchantApplication merchants;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void preparesOwnedPaymentUsingOrderAmountAndHidesItFromLegacyRepository() {
        var order = order("A");
        long paymentId = preparation.prepare(order.owner(), order.id());

        assertThat(status(order)).isEqualTo("PAYMENT_PENDING");
        assertThat(jdbc.queryForMap("""
                select merchant_id, order_id, ownership_mode, status, amount, provider_reference
                from payments where id = ?
                """, paymentId)).containsEntry("merchant_id", order.owner())
                .containsEntry("order_id", order.id())
                .containsEntry("ownership_mode", "TENANT")
                .containsEntry("status", "READY")
                .containsEntry("amount", new BigDecimal("10.25"))
                .containsEntry("provider_reference", null);
        assertThat(legacyPayments.findById(paymentId)).isEmpty();
        assertError(() -> commands.cancelCreated(order.owner(), order.id()), ErrorCode.INVALID_ORDER_STATE);
        assertError(() -> preparation.prepare(order.owner(), order.id()), ErrorCode.INVALID_ORDER_STATE);
        assertThat(paymentCount()).isOne();
    }

    @Test
    void rejectsForeignMissingLegacyAndCancelledOrdersWithoutPayment() {
        var order = order("A");
        long foreign = merchants.registerMerchant(new MerchantRegistrationCommand("B", "B", "UTC")).id();
        long legacy = jdbc.queryForObject("""
                insert into orders (customer_id, total_amount, currency, status, created_at, updated_at)
                values ('fixture', 10, 'KRW', 'CREATED', now(), now()) returning id
                """, Long.class);
        assertError(() -> preparation.prepare(foreign, order.id()), ErrorCode.ORDER_NOT_FOUND);
        assertError(() -> preparation.prepare(order.owner(), legacy), ErrorCode.ORDER_NOT_FOUND);
        assertError(() -> preparation.prepare(order.owner(), Long.MAX_VALUE), ErrorCode.ORDER_NOT_FOUND);
        assertThat(status(order)).isEqualTo("CREATED");

        commands.cancelCreated(order.owner(), order.id());
        assertError(() -> preparation.prepare(order.owner(), order.id()), ErrorCode.INVALID_ORDER_STATE);
        assertThat(status(order)).isEqualTo("CANCELLED");
        assertThat(paymentCount()).isZero();
    }

    @Test
    void paymentConstraintFailureRollsBackTheOrderClaim() {
        var order = order("A");
        jdbc.execute("""
                alter table payments add constraint ck_test_reject_ready check (status <> 'READY')
                """);
        try {
            assertThatThrownBy(() -> preparation.prepare(order.owner(), order.id()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .rootCause().hasMessageContaining("ck_test_reject_ready");
            assertThat(status(order)).isEqualTo("CREATED");
            assertThat(paymentCount()).isZero();
        } finally {
            jdbc.execute("alter table payments drop constraint ck_test_reject_ready");
        }
    }

    @Test
    void downstreamRecordRejectsAnOwnerThatDoesNotMatchTheOrder() {
        var order = order("A");
        var other = order("B");
        var tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> payments.insertReady(other.owner(), order.id(), BigDecimal.TEN)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fk_payments_owner_order");
        assertThat(paymentCount()).isZero();
    }

    @Test
    void transactionParticipantsRejectStandaloneCalls() {
        var order = order("A");
        assertThatThrownBy(() -> claims.claimPayment(order.owner(), order.id()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> payments.insertReady(order.owner(), order.id(), BigDecimal.TEN))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(status(order)).isEqualTo("CREATED");
        assertThat(paymentCount()).isZero();
    }

    @Test
    void twoPreparationsProduceOnlyOnePayment() throws Exception {
        var order = order("A");
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(race(barrier, () -> preparation.prepare(order.owner(), order.id())));
            var second = executor.submit(race(barrier, () -> preparation.prepare(order.owner(), order.id())));
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "INVALID_ORDER_STATE");
        }
        assertThat(status(order)).isEqualTo("PAYMENT_PENDING");
        assertThat(paymentCount()).isOne();
    }

    @Test
    void cancellationAndPreparationCannotBothWin() throws Exception {
        var order = order("A");
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var prepare = executor.submit(race(barrier, () -> preparation.prepare(order.owner(), order.id())));
            var cancel = executor.submit(race(barrier, () -> commands.cancelCreated(order.owner(), order.id())));
            String prepared = prepare.get(10, TimeUnit.SECONDS);
            String cancelled = cancel.get(10, TimeUnit.SECONDS);
            assertThat(java.util.List.of(prepared, cancelled)).containsExactlyInAnyOrder("SUCCESS", "INVALID_ORDER_STATE");
            assertThat(status(order)).isEqualTo("SUCCESS".equals(prepared) ? "PAYMENT_PENDING" : "CANCELLED");
            assertThat(paymentCount()).isEqualTo("SUCCESS".equals(prepared) ? 1L : 0L);
        }
    }

    private Callable<String> race(CyclicBarrier barrier, Runnable command) {
        return () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                command.run();
                return "SUCCESS";
            } catch (BusinessException exception) {
                return exception.getErrorCode().name();
            }
        };
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(code);
    }

    private Long paymentCount() {
        return jdbc.queryForObject("select count(*) from payments", Long.class);
    }

    private String status(OwnedOrder order) {
        return query.getOrder(order.owner(), order.id()).status();
    }

    private OwnedOrder order(String code) {
        long owner = merchants.registerMerchant(new MerchantRegistrationCommand(code, code, "UTC")).id();
        long store = merchants.registerStore(new StoreRegistrationCommand(owner, "STORE", "Store")).id();
        long id = commands.create(owner, new CreateTenantOrderCommand(store, "fixture",
                new BigDecimal("10.25"), "KRW")).orderId();
        return new OwnedOrder(owner, id);
    }

    private record OwnedOrder(long owner, long id) {
    }
}
