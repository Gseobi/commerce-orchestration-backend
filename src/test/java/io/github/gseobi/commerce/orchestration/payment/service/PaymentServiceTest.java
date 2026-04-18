package io.github.gseobi.commerce.orchestration.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.order.entity.OrderStatus;
import io.github.gseobi.commerce.orchestration.payment.api.PaymentResponse;
import io.github.gseobi.commerce.orchestration.payment.client.PaymentProviderClient;
import io.github.gseobi.commerce.orchestration.payment.client.PaymentProviderResult;
import io.github.gseobi.commerce.orchestration.payment.entity.Payment;
import io.github.gseobi.commerce.orchestration.payment.entity.PaymentStatus;
import io.github.gseobi.commerce.orchestration.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentProviderClient paymentProviderClient;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void approve_success() {
        Order order = new Order("customer-1", BigDecimal.valueOf(10000), "KRW", "normal", OrderStatus.PAYMENT_PENDING);
        Payment payment = new Payment(1L, PaymentStatus.APPROVED, order.getTotalAmount(), "MOCK-APPROVED");

        when(paymentProviderClient.approve(order.getId(), order.getTotalAmount(), order.getDescription()))
                .thenReturn(new PaymentProviderResult(PaymentStatus.APPROVED, "MOCK-APPROVED", "approved"));
        when(paymentRepository.save(ArgumentMatchers.any(Payment.class))).thenReturn(payment);

        PaymentResponse response = paymentService.approve(order.getId(), order.getTotalAmount(), order.getDescription());

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.providerReference()).isEqualTo("MOCK-APPROVED");
    }

    @Test
    void approve_failure() {
        Order order = new Order("customer-1", BigDecimal.valueOf(10000), "KRW", "FAIL_PAYMENT", OrderStatus.PAYMENT_PENDING);
        Payment payment = new Payment(1L, PaymentStatus.FAILED, order.getTotalAmount(), "MOCK-FAILED");

        when(paymentProviderClient.approve(order.getId(), order.getTotalAmount(), order.getDescription()))
                .thenReturn(new PaymentProviderResult(PaymentStatus.FAILED, "MOCK-FAILED", "failed"));
        when(paymentRepository.save(ArgumentMatchers.any(Payment.class))).thenReturn(payment);

        assertThatThrownBy(() -> paymentService.approve(order.getId(), order.getTotalAmount(), order.getDescription()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("failed");
    }
}
