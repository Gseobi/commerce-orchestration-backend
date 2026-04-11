package io.github.gseobi.commerce.orchestration.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxPublisherService outboxPublisherService;

    @Test
    void publishReadyEvents_marksPublished() {
        OutboxEvent outboxEvent = new OutboxEvent(1L, "commerce.notification.requested", "NOTIFICATION", "{}", OutboxStatus.READY);

        when(outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.READY)).thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.send("commerce.notification.requested", "1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(new ProducerRecord<>("commerce.notification.requested", "null", "{}"), null)));

        int publishedCount = outboxPublisherService.publishReadyEvents(100);

        assertThat(publishedCount).isEqualTo(1);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }
}
