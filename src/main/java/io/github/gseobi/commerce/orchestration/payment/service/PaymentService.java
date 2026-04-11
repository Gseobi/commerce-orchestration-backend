package io.github.gseobi.commerce.orchestration.payment.service;

import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.payment.dto.response.PaymentResponse;
import io.github.gseobi.commerce.orchestration.payment.entity.Payment;
import io.github.gseobi.commerce.orchestration.payment.entity.PaymentStatus;
import io.github.gseobi.commerce.orchestration.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentResponse approve(Order order) {
        Payment payment = new Payment(
                order,
                PaymentStatus.APPROVED,
                order.getTotalAmount(),
                "TODO-PROVIDER-REFERENCE"
        );
        // TODO 실제 외부 payment provider 연동 및 idempotency 처리 추가
        return PaymentResponse.from(paymentRepository.save(payment));
    }
}
