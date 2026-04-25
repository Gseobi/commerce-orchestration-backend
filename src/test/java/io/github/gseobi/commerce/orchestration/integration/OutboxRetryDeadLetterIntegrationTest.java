package io.github.gseobi.commerce.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.order.api.OrderFacade;
import io.github.gseobi.commerce.orchestration.order.dto.request.CreateOrderRequest;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderResponse;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxAdminView;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxApplication;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventSummary;
import io.github.gseobi.commerce.orchestration.outbox.service.OutboxPublisherService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest
class OutboxRetryDeadLetterIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private OutboxApplication outboxApplication;

    @Autowired
    private OutboxAdminApplication outboxAdminApplication;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @TestConfiguration
    static class FailingKafkaTemplateConfiguration {

        @Bean
        @Primary
        KafkaTemplate<String, String> kafkaTemplate() {
            KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("forced integration publish failure")));
            return kafkaTemplate;
        }
    }

    @Test
    void outboxFailure_transitionsFromRetryWaitToDeadLetter() {
        OrderResponse order = orderFacade.createOrder(new CreateOrderRequest(
                "customer-retry",
                BigDecimal.valueOf(9000),
                "KRW",
                "integration-retry-path"
        ));
        outboxApplication.appendOrderEvent(
                order.orderId(),
                "commerce.notification.requested",
                "NOTIFICATION_REQUESTED",
                "{\"orderId\":" + order.orderId() + "}"
        );

        int firstAttempt = outboxPublisherService.publishReadyEvents(10);
        assertThat(firstAttempt).isZero();

        List<OutboxEventSummary> firstEvents = outboxApplication.getOrderEvents(order.orderId());
        assertThat(firstEvents).hasSize(1);
        assertThat(firstEvents.getFirst().status()).isEqualTo("RETRY_WAIT");
        assertThat(firstEvents.getFirst().retryCount()).isEqualTo(1);
        assertThat(firstEvents.getFirst().nextAttemptAt()).isNotNull();
        assertThat(firstEvents.getFirst().failureCode()).isEqualTo("ExecutionException");

        int secondAttempt = outboxPublisherService.publishReadyEvents(10);
        assertThat(secondAttempt).isZero();

        List<OutboxEventSummary> secondEvents = outboxApplication.getOrderEvents(order.orderId());
        assertThat(secondEvents).hasSize(1);
        assertThat(secondEvents.getFirst().status()).isEqualTo("DEAD_LETTER");
        assertThat(secondEvents.getFirst().retryCount()).isEqualTo(2);
        assertThat(secondEvents.getFirst().deadLetteredAt()).isNotNull();
        assertThat(secondEvents.getFirst().failureReason()).contains("forced integration publish failure");
    }

    @Test
    void retryDeadLetterEvent_returnsRetryWaitResultWhenRepublishFails() {
        OrderResponse order = orderFacade.createOrder(new CreateOrderRequest(
                "customer-retry",
                BigDecimal.valueOf(9000),
                "KRW",
                "integration-admin-retry-failure-path"
        ));
        outboxApplication.appendOrderEvent(
                order.orderId(),
                "commerce.notification.requested",
                "NOTIFICATION_REQUESTED",
                "{\"orderId\":" + order.orderId() + "}"
        );

        outboxPublisherService.publishReadyEvents(10);
        outboxPublisherService.publishReadyEvents(10);

        OutboxEventSummary deadLetterEvent = outboxApplication.getOrderEvents(order.orderId()).getFirst();
        assertThat(deadLetterEvent.status()).isEqualTo("DEAD_LETTER");

        OutboxAdminView retried = outboxAdminApplication.retryDeadLetterEvent(deadLetterEvent.outboxEventId());

        assertThat(retried.outboxEventId()).isEqualTo(deadLetterEvent.outboxEventId());
        assertThat(retried.aggregateId()).isEqualTo(order.orderId());
        assertThat(retried.eventType()).isEqualTo("NOTIFICATION_REQUESTED");
        assertThat(retried.previousStatus()).isEqualTo("DEAD_LETTER");
        assertThat(retried.status()).isEqualTo("RETRY_WAIT");
        assertThat(retried.retryCount()).isEqualTo(1);
        assertThat(retried.failureCode()).isEqualTo("ExecutionException");
        assertThat(retried.failureReason()).contains("forced integration publish failure");
        assertThat(retried.nextAttemptAt()).isNotNull();
        assertThat(retried.deadLetteredAt()).isNull();
    }
}
