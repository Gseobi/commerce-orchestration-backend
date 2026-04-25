package io.github.gseobi.commerce.orchestration.infrastructure.kafka;

import io.github.gseobi.commerce.orchestration.config.OutboxProperties;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventPublisher;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxPublishResult;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KafkaOutboxEventPublisher implements OutboxEventPublisher {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    @Override
    public OutboxPublishResult publish(OutboxEvent event) {
        try {
            SendResult<String, String> result = kafkaTemplate
                    .send(event.getTopic(), String.valueOf(event.getOrderId()), event.getPayload())
                    .get(outboxProperties.publishTimeout().toSeconds(), TimeUnit.SECONDS);
            if (result != null) {
                return OutboxPublishResult.success();
            }
            return OutboxPublishResult.failure("PublishResultNull", "Kafka publish result was null.");
        } catch (Exception exception) {
            return OutboxPublishResult.failure(
                    exception.getClass().getSimpleName(),
                    buildFailureReason(exception)
            );
        }
    }

    private String buildFailureReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getName();
        }
        return message.length() > MAX_FAILURE_REASON_LENGTH
                ? message.substring(0, MAX_FAILURE_REASON_LENGTH)
                : message;
    }
}
