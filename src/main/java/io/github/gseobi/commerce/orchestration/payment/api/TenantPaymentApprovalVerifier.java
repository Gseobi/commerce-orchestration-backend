package io.github.gseobi.commerce.orchestration.payment.api;

import java.math.BigDecimal;
import java.util.Optional;

/** Trusted adapter SPI. Verify an authoritative provider approval and its merchant/payment binding, never caller claims. */
public interface TenantPaymentApprovalVerifier {

    Optional<Evidence> verify(Candidate candidate);

    record Candidate(long merchantId, long orderId, long paymentId, BigDecimal amount, String currency) {
    }

    record Evidence(Candidate binding, String providerTransactionId) {

        @Override
        public String toString() {
            return "Evidence[redacted]";
        }
    }
}
