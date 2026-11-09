package io.github.gseobi.commerce.orchestration.orchestration.service;

import io.github.gseobi.commerce.orchestration.audit.api.TenantOrderAuditRecorder;
import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderPaymentTransition;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentApprovalRecords;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentApprovalVerifier;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentApprovalVerifier.Candidate;
import io.github.gseobi.commerce.orchestration.payment.api.TenantPaymentApprovalVerifier.Evidence;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
class TenantPaymentApproval {

    private final TenantOrderQuery orders;
    private final TenantOrderPaymentTransition orderTransition;
    private final TenantPaymentApprovalRecords payments;
    private final ObjectProvider<TenantPaymentApprovalVerifier> verifiers;
    private final TenantOrderAuditRecorder audit;
    private final TransactionTemplate transactions;

    @Transactional(propagation = Propagation.NEVER)
    public void confirm(Long merchantId, Long orderId, long paymentId) {
        if (paymentId <= 0) {
            throw new IllegalArgumentException("Positive paymentId is required.");
        }
        var order = orders.getOrder(merchantId, orderId);
        if (!"PAYMENT_PENDING".equals(order.status())) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE);
        }
        var candidate = new Candidate(order.merchantId(), order.orderId(), paymentId,
                order.totalAmount(), order.currency());
        if (!payments.isReady(candidate.merchantId(), candidate.orderId(), paymentId, candidate.amount())) {
            throw rejected();
        }
        var verifier = verifiers.getIfUnique();
        if (verifier == null) {
            throw rejected();
        }
        Evidence evidence = verifier.verify(candidate).filter(result -> matches(candidate, result))
                .orElseThrow(this::rejected);
        try {
            transactions.executeWithoutResult(tx -> {
                orderTransition.markPaid(candidate.merchantId(), candidate.orderId(), candidate.amount(), candidate.currency());
                payments.approveReady(candidate.merchantId(), candidate.orderId(), candidate.paymentId(),
                        candidate.amount(), evidence.providerTransactionId());
                audit.recordPaymentApproved(candidate.merchantId(), candidate.orderId());
            });
        } catch (DataIntegrityViolationException exception) {
            // Do not expose database details containing the provider transaction identifier.
            throw rejected();
        }
    }

    private boolean matches(Candidate expected, Evidence evidence) {
        Candidate actual = evidence.binding();
        String reference = evidence.providerTransactionId();
        return actual != null && actual.merchantId() == expected.merchantId()
                && actual.orderId() == expected.orderId() && actual.paymentId() == expected.paymentId()
                && actual.amount() != null && expected.amount().compareTo(actual.amount()) == 0
                && expected.currency().equals(actual.currency())
                && reference != null && !reference.isBlank() && reference.length() <= 100
                && reference.equals(reference.strip()) && reference.chars().noneMatch(Character::isISOControl);
    }

    private BusinessException rejected() {
        return new BusinessException(ErrorCode.PAYMENT_FAILED);
    }
}
