package io.github.gseobi.commerce.orchestration.outbox.service;

import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OutboxPublisherService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventRepository outboxEventRepository;

    public OutboxPublisherService(
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxEventRepository outboxEventRepository
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public int publishReadyEvents(int batchSize) {
        List<OutboxEvent> readyEvents = outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.READY);
        int publishedCount = 0;
        for (OutboxEvent readyEvent : readyEvents.stream().limit(batchSize).toList()) {
            try {
                SendResult<String, String> result = kafkaTemplate
                        .send(readyEvent.getTopic(), String.valueOf(readyEvent.getOrderId()), readyEvent.getPayload())
                        .get(3, TimeUnit.SECONDS);
                if (result != null) {
                    readyEvent.markPublished();
                    publishedCount++;
                }
            } catch (Exception exception) {
                readyEvent.markFailed(exception.getMessage());
            }
        }
        return publishedCount;
    }
}
