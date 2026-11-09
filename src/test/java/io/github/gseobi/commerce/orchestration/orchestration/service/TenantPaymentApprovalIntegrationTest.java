package io.github.gseobi.commerce.orchestration.orchestration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.integration.TestcontainersIntegrationSupport;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreRegistrationCommand;
import io.github.gseobi.commerce.orchestration.order.api.CreateTenantOrderCommand;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderCommands;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentApprovalVerifier;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentApprovalVerifier.Candidate;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentApprovalVerifier.Evidence;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest
class TenantPaymentApprovalIntegrationTest extends TestcontainersIntegrationSupport {

    @MockitoBean private TenantPaymentApprovalVerifier verifier;
    @Autowired private TenantPaymentApproval approval;
    @Autowired private TenantPaymentPreparation preparation;
    @Autowired private TenantOrderCommands orders;
    @Autowired private TenantOrderQuery query;
    @Autowired private MerchantApplication merchants;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;

    @Test
    void recordsOnlyVerifiedApprovalAndRejectsReplayWithoutRepeatingAudit() {
        var payment = prepare("A");
        when(verifier.verify(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Candidate candidate = invocation.getArgument(0);
            assertThat(candidate).isEqualTo(new Candidate(payment.merchant(), payment.order(), payment.id(),
                    new BigDecimal("10.25"), "KRW"));
            return Optional.of(new Evidence(candidate, "fixture-approval"));
        });

        confirm(payment);

        assertThat(query.getOrder(payment.merchant(), payment.order()).status()).isEqualTo("PAID");
        assertThat(jdbc.queryForMap("select status, provider_transaction_id, version from payments where id = ?",
                payment.id())).containsEntry("status", "APPROVED")
                .containsEntry("provider_transaction_id", "fixture-approval").containsEntry("version", 1L);
        assertError(() -> confirm(payment), ErrorCode.INVALID_ORDER_STATE);
        assertThat(approvalAudits()).isOne();
        assertThat(jdbc.queryForObject("select count(*) from settlements", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification_events", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isZero();
    }

    @Test
    void rejectsForeignOwnerAndMismatchedPaymentBeforeVerification() {
        var first = prepare("A");
        var second = prepare("B");
        assertError(() -> approval.confirm(second.merchant(), first.order(), first.id()), ErrorCode.ORDER_NOT_FOUND);
        assertError(() -> approval.confirm(first.merchant(), first.order(), second.id()), ErrorCode.PAYMENT_FAILED);
        verifyNoInteractions(verifier);
        assertPending(first);
        assertPending(second);
    }

    @Test
    void missingEvidenceCannotPromoteReady() {
        var payment = prepare("A");
        when(verifier.verify(any())).thenReturn(Optional.empty());
        assertError(() -> confirm(payment), ErrorCode.PAYMENT_FAILED);
        assertPending(payment);
        assertThat(approvalAudits()).isZero();
    }

    @ParameterizedTest
    @EnumSource(Mismatch.class)
    void rejectsEvidenceThatDoesNotBindToThePreparedPayment(Mismatch mismatch) {
        var payment = prepare("A");
        when(verifier.verify(any())).thenAnswer(invocation -> {
            Candidate expected = invocation.getArgument(0);
            var binding = new Candidate(
                    expected.merchantId() + (mismatch == Mismatch.MERCHANT ? 1 : 0),
                    expected.orderId() + (mismatch == Mismatch.ORDER ? 1 : 0),
                    expected.paymentId() + (mismatch == Mismatch.PAYMENT ? 1 : 0),
                    mismatch == Mismatch.AMOUNT ? BigDecimal.ONE : expected.amount(),
                    mismatch == Mismatch.CURRENCY ? "USD" : expected.currency());
            return Optional.of(new Evidence(binding, mismatch == Mismatch.REFERENCE ? " " : "fixture-approval"));
        });
        assertError(() -> confirm(payment), ErrorCode.PAYMENT_FAILED);
        assertPending(payment);
        assertThat(approvalAudits()).isZero();
    }

    @Test
    void rollsBackOrderWhenPaymentChangesDuringVerification() {
        var payment = prepare("A");
        when(verifier.verify(any())).thenAnswer(invocation -> {
            jdbc.update("update payments set amount = 11 where id = ?", payment.id());
            return Optional.of(new Evidence(invocation.getArgument(0), "fixture-stale"));
        });
        assertError(() -> confirm(payment), ErrorCode.PAYMENT_FAILED);
        assertPending(payment);
        assertThat(approvalAudits()).isZero();
    }

    @Test
    void duplicateProviderReferenceCannotApproveAnotherTenantPayment() {
        var first = prepare("A");
        var second = prepare("B");
        when(verifier.verify(any())).thenAnswer(invocation ->
                Optional.of(new Evidence(invocation.getArgument(0), "fixture-duplicate")));
        confirm(first);
        assertError(() -> confirm(second), ErrorCode.PAYMENT_FAILED);
        assertPending(second);
        assertThat(approvalAudits()).isOne();
    }

    @Test
    void auditWriteFailureRollsBackBothTransitions() {
        var payment = prepare("A");
        when(verifier.verify(any())).thenAnswer(invocation ->
                Optional.of(new Evidence(invocation.getArgument(0), "fixture-audit")));
        jdbc.execute("""
                alter table audit_logs add constraint ck_test_block_approval
                    check (action <> 'TENANT_PAYMENT_APPROVED')
                """);
        try {
            assertError(() -> confirm(payment), ErrorCode.PAYMENT_FAILED);
            assertPending(payment);
            assertThat(approvalAudits()).isZero();
        } finally {
            jdbc.execute("alter table audit_logs drop constraint ck_test_block_approval");
        }
    }

    @Test
    void verifierCannotRunInACallerTransaction() {
        var payment = prepare("A");
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> confirm(payment)))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(verifier);
        assertPending(payment);
    }

    private void assertPending(Prepared payment) {
        assertThat(query.getOrder(payment.merchant(), payment.order()).status()).isEqualTo("PAYMENT_PENDING");
        assertThat(jdbc.queryForMap("select status, provider_transaction_id from payments where id = ?", payment.id()))
                .containsEntry("status", "READY").containsEntry("provider_transaction_id", null);
    }

    private Long approvalAudits() {
        return jdbc.queryForObject("select count(*) from audit_logs where action = 'TENANT_PAYMENT_APPROVED'", Long.class);
    }

    private void confirm(Prepared payment) {
        approval.confirm(payment.merchant(), payment.order(), payment.id());
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(code);
    }

    private Prepared prepare(String code) {
        long merchant = merchants.registerMerchant(new MerchantRegistrationCommand(code, code, "UTC")).id();
        long store = merchants.registerStore(new StoreRegistrationCommand(merchant, "STORE", "Store")).id();
        long order = orders.create(merchant, new CreateTenantOrderCommand(store, "fixture",
                new BigDecimal("10.25"), "KRW")).orderId();
        return new Prepared(merchant, order, preparation.prepare(merchant, order));
    }

    private record Prepared(long merchant, long order, long id) {
    }

    private enum Mismatch {
        MERCHANT, ORDER, PAYMENT, AMOUNT, CURRENCY, REFERENCE
    }
}
