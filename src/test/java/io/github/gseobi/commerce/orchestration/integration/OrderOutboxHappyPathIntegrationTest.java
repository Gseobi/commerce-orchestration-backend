package io.github.gseobi.commerce.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gseobi.commerce.orchestration.infrastructure.kafka.KafkaTopicNames;
import io.github.gseobi.commerce.orchestration.order.api.OrderFacade;
import io.github.gseobi.commerce.orchestration.order.dto.request.CreateOrderRequest;
import io.github.gseobi.commerce.orchestration.order.dto.response.OrderResponse;
import io.github.gseobi.commerce.orchestration.orchestration.dto.response.OrderFlowResponse;
import io.github.gseobi.commerce.orchestration.outbox.service.OutboxPublisherService;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest
class OrderOutboxHappyPathIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Test
    void orchestrate_happyPath_publishesOutboxEventsToKafka() {
        OrderResponse order = orderFacade.createOrder(new CreateOrderRequest(
                "customer-integration",
                BigDecimal.valueOf(12000),
                "KRW",
                "integration-happy-path"
        ));

        OrderFlowResponse orchestrationResult = orderFacade.orchestrate(order.orderId());
        assertThat(orchestrationResult.orderStatus()).isEqualTo("COMPLETED");
        assertThat(orchestrationResult.outboxEvents()).hasSize(2);

        int publishedCount = outboxPublisherService.publishReadyEvents(10);
        assertThat(publishedCount).isEqualTo(2);

        OrderFlowResponse publishedFlow = orderFacade.getOrderFlow(order.orderId());
        assertThat(publishedFlow.outboxEvents())
                .extracting(OrderFlowResponse.OutboxResponse::status)
                .containsOnly("PUBLISHED");

        try (var consumer = createConsumer("outbox-happy-path")) {
            Set<String> topics = new HashSet<>();
            Set<String> payloads = new HashSet<>();
            consumer.subscribe(java.util.List.of(
                    KafkaTopicNames.SETTLEMENT_REQUESTED,
                    KafkaTopicNames.NOTIFICATION_REQUESTED
            ));

            org.awaitility.Awaitility.await()
                    .atMost(awaitTimeout())
                    .untilAsserted(() -> {
                        ConsumerRecords<String, String> records = consumer.poll(java.time.Duration.ofMillis(500));
                        for (ConsumerRecord<String, String> record : records) {
                            topics.add(record.topic());
                            payloads.add(record.value());
                        }

                        assertThat(topics).containsExactlyInAnyOrder(
                                KafkaTopicNames.SETTLEMENT_REQUESTED,
                                KafkaTopicNames.NOTIFICATION_REQUESTED
                        );
                        assertThat(payloads).allMatch(payload -> payload.contains("\"orderId\":" + order.orderId()));
                    });
        }
    }
}
