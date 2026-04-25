package io.github.gseobi.commerce.orchestration.payment.repository;

import io.github.gseobi.commerce.orchestration.payment.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByOrderId(Long orderId);

    List<Payment> findAllByOrderIdOrderByIdAsc(Long orderId);

    Payment findTopByOrderIdOrderByIdDesc(Long orderId);

    Optional<Payment> findByPaymentRequestId(String paymentRequestId);

    Optional<Payment> findByProviderTransactionId(String providerTransactionId);
}
