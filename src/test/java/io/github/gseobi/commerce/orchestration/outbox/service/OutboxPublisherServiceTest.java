package io.github.gseobi.commerce.orchestration.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.config.OutboxProperties;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    private static final OutboxProperties OUTBOX_PROPERTIES = new OutboxProperties(
            false,
            Duration.ofSeconds(5),
            100,
            2,
            Duration.ZERO,
            1.0,
            Duration.ZERO,
            Duration.ofSeconds(1)
    );

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxPublisherService outboxPublisherService;

    @BeforeEach
    void setUp() {
        outboxPublisherService = new OutboxPublisherService(
                kafkaTemplate,
                outboxEventRepository,
                OUTBOX_PROPERTIES
        );
    }

    @Test
    void publishReadyEvents_marksPublished() {
        OutboxEvent outboxEvent = new OutboxEvent(1L, "commerce.notification.requested", "NOTIFICATION", "{}", OutboxStatus.READY);

        when(outboxEventRepository.findPublishableEvents(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.send("commerce.notification.requested", "1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(
                        new SendResult<>(new ProducerRecord<>("commerce.notification.requested", "1", "{}"), null)
                ));

        int publishedCount = outboxPublisherService.publishReadyEvents(100);

        assertThat(publishedCount).isEqualTo(1);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outboxEvent.getRetryCount()).isZero();
    }

    @Test
    void publishReadyEvents_schedulesRetryBeforeDeadLetter() {
        OutboxEvent outboxEvent = new OutboxEvent(1L, "commerce.notification.requested", "NOTIFICATION", "{}", OutboxStatus.READY);

        when(outboxEventRepository.findPublishableEvents(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.send("commerce.notification.requested", "1", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new CompletionException("broker unavailable", new IllegalStateException("broker unavailable"))));

        int publishedCount = outboxPublisherService.publishReadyEvents(100);

        assertThat(publishedCount).isZero();
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.RETRY_WAIT);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getNextAttemptAt()).isNotNull();
        assertThat(outboxEvent.getFailureReason()).contains("broker unavailable");
    }

    @Test
    void publishReadyEvents_marksDeadLetterAfterMaxRetryCount() {
        OutboxEvent outboxEvent = new OutboxEvent(1L, "commerce.notification.requested", "NOTIFICATION", "{}", OutboxStatus.RETRY_WAIT);
        outboxEvent.markRetryWaiting("IllegalStateException", "first failure", LocalDateTime.now());

        when(outboxEventRepository.findPublishableEvents(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.send("commerce.notification.requested", "1", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new CompletionException("still unavailable", new IllegalStateException("still unavailable"))));

        int publishedCount = outboxPublisherService.publishReadyEvents(100);

        assertThat(publishedCount).isZero();
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(2);
        assertThat(outboxEvent.getDeadLetteredAt()).isNotNull();
        assertThat(outboxEvent.getFailureReason()).contains("still unavailable");
    }
}
