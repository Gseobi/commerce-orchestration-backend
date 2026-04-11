package io.github.gseobi.commerce.orchestration.payment.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.payment.api.PaymentApplication;
import io.github.gseobi.commerce.orchestration.payment.client.PaymentProviderClient;
import io.github.gseobi.commerce.orchestration.payment.client.PaymentProviderResult;
import io.github.gseobi.commerce.orchestration.payment.dto.response.PaymentResponse;
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
        PaymentProviderResult providerResult = paymentProviderClient.approve(orderId, description);
        Payment payment = new Payment(orderId, providerResult.status(), amount, providerResult.providerReference());
        paymentRepository.save(payment);
        if (providerResult.status() != PaymentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, providerResult.message());
        }
        return PaymentResponse.from(paymentRepository.save(payment));
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
        return PaymentResponse.from(latestPayment);
    }

    @Override
    public List<String> getPaymentStatuses(Long orderId) {
        return paymentRepository.findAllByOrderId(orderId)
                .stream()
                .map(payment -> payment.getStatus().name())
                .toList();
    }
}
