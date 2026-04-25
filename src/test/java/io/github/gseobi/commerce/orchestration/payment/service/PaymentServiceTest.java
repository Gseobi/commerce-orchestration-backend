package io.github.gseobi.commerce.orchestration.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String PAYMENT_REQUEST_ID = "ORDER-1-PAYMENT-APPROVE";

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

        when(paymentRepository.findByPaymentRequestId(PAYMENT_REQUEST_ID)).thenReturn(Optional.empty());
        when(paymentProviderClient.approve(order.getId(), order.getTotalAmount(), order.getDescription()))
                .thenReturn(new PaymentProviderResult(PaymentStatus.APPROVED, "MOCK-APPROVED", "approved"));
        when(paymentRepository.save(ArgumentMatchers.any(Payment.class))).thenReturn(payment);

        PaymentResponse response = paymentService.approve(
                PAYMENT_REQUEST_ID,
                order.getId(),
                order.getTotalAmount(),
                order.getDescription()
        );

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.providerReference()).isEqualTo("MOCK-APPROVED");
    }

    @Test
    void approve_failure() {
        Order order = new Order("customer-1", BigDecimal.valueOf(10000), "KRW", "FAIL_PAYMENT", OrderStatus.PAYMENT_PENDING);
        Payment payment = new Payment(1L, PaymentStatus.FAILED, order.getTotalAmount(), "MOCK-FAILED");

        when(paymentRepository.findByPaymentRequestId(PAYMENT_REQUEST_ID)).thenReturn(Optional.empty());
        when(paymentProviderClient.approve(order.getId(), order.getTotalAmount(), order.getDescription()))
                .thenReturn(new PaymentProviderResult(PaymentStatus.FAILED, "MOCK-FAILED", "failed"));
        when(paymentRepository.save(ArgumentMatchers.any(Payment.class))).thenReturn(payment);

        assertThatThrownBy(() -> paymentService.approve(
                PAYMENT_REQUEST_ID,
                order.getId(),
                order.getTotalAmount(),
                order.getDescription()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void approve_idempotent_replay_reuses_existing_payment_without_provider_call() {
        Order order = new Order("customer-1", BigDecimal.valueOf(10000), "KRW", "normal", OrderStatus.PAYMENT_PENDING);
        Payment payment = new Payment(
                PAYMENT_REQUEST_ID,
                1L,
                PaymentStatus.APPROVED,
                order.getTotalAmount(),
                "MOCK-APPROVED",
                null
        );

        when(paymentRepository.findByPaymentRequestId(PAYMENT_REQUEST_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(payment));
        when(paymentProviderClient.approve(1L, order.getTotalAmount(), order.getDescription()))
                .thenReturn(new PaymentProviderResult(PaymentStatus.APPROVED, "MOCK-APPROVED", "approved"));
        when(paymentRepository.save(ArgumentMatchers.any(Payment.class))).thenReturn(payment);

        PaymentResponse firstResponse = paymentService.approve(
                PAYMENT_REQUEST_ID,
                1L,
                order.getTotalAmount(),
                order.getDescription()
        );
        PaymentResponse secondResponse = paymentService.approve(
                PAYMENT_REQUEST_ID,
                1L,
                order.getTotalAmount(),
                order.getDescription()
        );

        assertThat(firstResponse.status()).isEqualTo("APPROVED");
        assertThat(secondResponse.status()).isEqualTo("APPROVED");
        verify(paymentRepository, times(1)).save(ArgumentMatchers.any(Payment.class));
        verify(paymentProviderClient, times(1)).approve(1L, order.getTotalAmount(), order.getDescription());
    }

    @Test
    void getPaymentStatuses_keeps_order_by_payment_id_ascending() {
        when(paymentRepository.findAllByOrderIdOrderByIdAsc(1L)).thenReturn(List.of(
                new Payment(1L, PaymentStatus.FAILED, BigDecimal.valueOf(10000), "MOCK-FAILED"),
                new Payment(1L, PaymentStatus.APPROVED, BigDecimal.valueOf(10000), "MOCK-APPROVED")
        ));

        List<String> statuses = paymentService.getPaymentStatuses(1L);

        assertThat(statuses).containsExactly("FAILED", "APPROVED");
    }
}
