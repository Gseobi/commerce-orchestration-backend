package io.github.gseobi.commerce.orchestration.orchestration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gseobi.commerce.orchestration.audit.api.TenantOrderAuditRecorder;
import io.github.gseobi.commerce.orchestration.audit.repository.AuditLogRepository;
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
import io.github.gseobi.commerce.orchestration.orchestration.repository.OrchestrationStepRepository;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentRecords;
import io.github.gseobi.commerce.orchestration.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    @Autowired private TenantPaymentApproval approval;
    @Autowired private TenantOrderCommands commands;
    @Autowired private TenantOrderPaymentClaim claims;
    @Autowired private TenantOrderQuery query;
    @Autowired private TenantPaymentRecords payments;
    @Autowired private PaymentRepository legacyPayments;
    @Autowired private TenantOrderAuditRecorder audit;
    @Autowired private TenantPaymentPreparationSteps steps;
    @Autowired private AuditLogRepository legacyAudit;
    @Autowired private OrchestrationStepRepository legacySteps;
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
        assertThat(jdbc.queryForMap("""
                select merchant_id, order_id, ownership_mode, action, detail from audit_logs
                """)).containsEntry("merchant_id", order.owner()).containsEntry("order_id", order.id())
                .containsEntry("ownership_mode", "TENANT").containsEntry("action", "TENANT_PAYMENT_PREPARED")
                .containsEntry("detail", "Internal payment prepared; provider not called.");
        assertThat(jdbc.queryForMap("""
                select merchant_id, order_id, ownership_mode, step_type, status from orchestration_steps
                """)).containsEntry("merchant_id", order.owner()).containsEntry("order_id", order.id())
                .containsEntry("ownership_mode", "TENANT").containsEntry("step_type", "PAYMENT_PREPARATION")
                .containsEntry("status", "SUCCESS");
        assertThat(legacyAudit.findAllByOrderIdOrderByIdAsc(order.id())).isEmpty();
        assertThat(legacySteps.findAllByOrderIdOrderByIdAsc(order.id())).isEmpty();
        assertError(() -> commands.cancelCreated(order.owner(), order.id()), ErrorCode.INVALID_ORDER_STATE);
        assertError(() -> preparation.prepare(order.owner(), order.id()), ErrorCode.INVALID_ORDER_STATE);
        assertThat(paymentCount()).isOne();
        assertTraceCount(1);
    }

    @Test
    void noProductionVerifierMeansReadyCannotBecomePaid() {
        var order = order("A");
        long payment = preparation.prepare(order.owner(), order.id());
        assertError(() -> approval.confirm(order.owner(), order.id(), payment), ErrorCode.PAYMENT_FAILED);
        assertThat(status(order)).isEqualTo("PAYMENT_PENDING");
        assertThat(jdbc.queryForObject("select status from payments where id = ?", String.class, payment))
                .isEqualTo("READY");
        assertTraceCount(1);
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
        assertTraceCount(0);
    }

    @ParameterizedTest
    @EnumSource(RejectedWrite.class)
    void writeFailureRollsBackOrderAndAllRecords(RejectedWrite write) {
        var order = order("A");
        jdbc.execute("alter table " + write.table + " add constraint ck_test_reject_write check (" + write.check + ")");
        try {
            assertThatThrownBy(() -> preparation.prepare(order.owner(), order.id()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .rootCause().hasMessageContaining("ck_test_reject_write");
            assertThat(status(order)).isEqualTo("CREATED");
            assertThat(paymentCount()).isZero();
            assertTraceCount(0);
        } finally {
            jdbc.execute("alter table " + write.table + " drop constraint ck_test_reject_write");
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
    void tracePortsRejectAnOwnerThatDoesNotMatchTheOrder() {
        var order = order("A");
        var other = order("B");
        var tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> steps.recordSuccess(other.owner(), order.id())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fk_orchestration_steps_owner_order");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> audit.recordPaymentPrepared(other.owner(), order.id())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("fk_audit_logs_owner_order");
        assertTraceCount(0);
    }

    @Test
    void transactionParticipantsRejectStandaloneCalls() {
        var order = order("A");
        assertThatThrownBy(() -> claims.claimPayment(order.owner(), order.id()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> payments.insertReady(order.owner(), order.id(), BigDecimal.TEN))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> steps.recordSuccess(order.owner(), order.id()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> audit.recordPaymentPrepared(order.owner(), order.id()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(status(order)).isEqualTo("CREATED");
        assertThat(paymentCount()).isZero();
        assertTraceCount(0);
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
        assertTraceCount(1);
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
            assertTraceCount("SUCCESS".equals(prepared) ? 1 : 0);
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

    private void assertTraceCount(long count) {
        assertThat(jdbc.queryForObject("select count(*) from audit_logs", Long.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("select count(*) from orchestration_steps", Long.class)).isEqualTo(count);
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

    private enum RejectedWrite {
        PAYMENT("payments", "status <> 'READY'"),
        STEP("orchestration_steps", "step_type <> 'PAYMENT_PREPARATION'"),
        AUDIT("audit_logs", "action <> 'TENANT_PAYMENT_PREPARED'");

        private final String table;
        private final String check;

        RejectedWrite(String table, String check) {
            this.table = table;
            this.check = check;
        }
    }
}
