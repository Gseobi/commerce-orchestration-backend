package io.github.gseobi.commerce.orchestration.payment.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.payment.api.PaymentApplication;
import io.github.gseobi.commerce.orchestration.payment.api.PaymentResponse;
import io.github.gseobi.commerce.orchestration.payment.client.PaymentProviderClient;
import io.github.gseobi.commerce.orchestration.payment.client.PaymentProviderResult;
import io.github.gseobi.commerce.orchestration.payment.entity.Payment;
import io.github.gseobi.commerce.orchestration.payment.entity.PaymentStatus;
import io.github.gseobi.commerce.orchestration.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PaymentService implements PaymentApplication {

    private final PaymentProviderClient paymentProviderClient;
    private final PaymentRepository paymentRepository;

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public PaymentResponse approve(Long orderId, BigDecimal amount, String description) {
        PaymentProviderResult providerResult = paymentProviderClient.approve(orderId, amount, description);
        Payment payment = new Payment(orderId, providerResult.status(), amount, providerResult.providerReference());
        paymentRepository.save(payment);
        if (providerResult.status() != PaymentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, providerResult.message());
        }
        Payment savedPayment = paymentRepository.save(payment);
        return PaymentResponse.of(
                savedPayment.getId(),
                savedPayment.getOrderId(),
                savedPayment.getStatus().name(),
                savedPayment.getAmount(),
                savedPayment.getProviderReference()
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public PaymentResponse cancelLatestApprovedPayment(Long orderId, String reason) {
        Payment latestPayment = paymentRepository.findTopByOrderIdOrderByIdDesc(orderId);
        if (latestPayment == null) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, "취소할 결제 이력이 없습니다.");
        }
        PaymentProviderResult providerResult = paymentProviderClient.cancel(orderId, reason);
        latestPayment.changeStatus(PaymentStatus.CANCELLED);
        latestPayment.changeProviderReference(providerResult.providerReference());
        return PaymentResponse.of(
                latestPayment.getId(),
                latestPayment.getOrderId(),
                latestPayment.getStatus().name(),
                latestPayment.getAmount(),
                latestPayment.getProviderReference()
        );
    }

    @Override
    public List<String> getPaymentStatuses(Long orderId) {
        return paymentRepository.findAllByOrderIdOrderByIdAsc(orderId)
                .stream()
                .map(payment -> payment.getStatus().name())
                .toList();
    }
}
