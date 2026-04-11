package io.github.gseobi.commerce.orchestration.payment.repository;

import io.github.gseobi.commerce.orchestration.payment.entity.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByOrderId(Long orderId);

    Payment findTopByOrderIdOrderByIdDesc(Long orderId);
}
