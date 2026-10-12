package io.github.gseobi.commerce.orchestration.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.config.OutboxProperties;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxPublishMessage;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaOutboxEventPublisherTest {
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxProperties properties = new OutboxProperties(false, Duration.ofSeconds(5), 100, 2,
            Duration.ZERO, 1.0, Duration.ZERO, Duration.ofSeconds(1));
    private final KafkaOutboxEventPublisher publisher = new KafkaOutboxEventPublisher(kafka, properties);
    private final OutboxPublishMessage message = new OutboxPublishMessage(42L, "commerce.notification.requested",
            "NOTIFICATION_REQUESTED", "{\"orderId\":42,\"eventType\":\"NOTIFICATION_REQUESTED\"}");

    @Test
    void publishesSnapshotWithoutJpaEntityAndPreservesKeyAndPayload() {
        when(kafka.send(message.topic(), "42", message.payload()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        assertThat(publisher.publish(message).isSuccess()).isTrue();
        verify(kafka).send(message.topic(), "42", message.payload());
    }

    @Test
    void nullBrokerResultIsFailure() {
        when(kafka.send(message.topic(), "42", message.payload())).thenReturn(CompletableFuture.completedFuture(null));
        assertThat(publisher.publish(message).failureCode()).isEqualTo("PublishResultNull");
    }

    @Test
    void failedBrokerResultIsBoundedFailure() {
        when(kafka.send(message.topic(), "42", message.payload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("x".repeat(1500))));
        var result = publisher.publish(message);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureCode()).isEqualTo("ExecutionException");
        assertThat(result.failureReason()).hasSize(1000);
    }
}
