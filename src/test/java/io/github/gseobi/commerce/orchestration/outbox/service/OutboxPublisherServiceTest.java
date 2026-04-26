package io.github.gseobi.commerce.orchestration.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.common.metrics.CommerceRecoveryMetrics;
import io.github.gseobi.commerce.orchestration.config.OutboxProperties;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxEventPublisher;
import io.github.gseobi.commerce.orchestration.outbox.api.OutboxPublishResult;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

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
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private SimpleMeterRegistry meterRegistry;
    private OutboxPublisherService outboxPublisherService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        outboxPublisherService = new OutboxPublisherService(
                outboxEventPublisher,
                outboxEventRepository,
                OUTBOX_PROPERTIES,
                new CommerceRecoveryMetrics(meterRegistry)
        );
    }

    @Test
    void publishReadyEvents_marksPublished() {
        OutboxEvent outboxEvent = outboxEvent(1L, OutboxStatus.READY);

        when(outboxEventRepository.findPublishableEvents(anyList(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(outboxEvent));
        when(outboxEventRepository.claimPublishableEvent(eq(1L), eq(List.of(OutboxStatus.READY, OutboxStatus.RETRY_WAIT)), any(LocalDateTime.class)))
                .thenReturn(1);
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(outboxEvent));
        when(outboxEventPublisher.publish(outboxEvent)).thenReturn(OutboxPublishResult.success());

        int publishedCount = outboxPublisherService.publishReadyEvents(100);

        assertThat(publishedCount).isEqualTo(1);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outboxEvent.getRetryCount()).isZero();
        assertThat(counter("commerce.outbox.publish.attempts")).isEqualTo(1.0);
        assertThat(counter("commerce.outbox.publish.success")).isEqualTo(1.0);
    }

    @Test
    void publishReadyEvents_schedulesRetryBeforeDeadLetter() {
        OutboxEvent outboxEvent = outboxEvent(1L, OutboxStatus.READY);

        when(outboxEventRepository.findPublishableEvents(anyList(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(outboxEvent));
        when(outboxEventRepository.claimPublishableEvent(eq(1L), eq(List.of(OutboxStatus.READY, OutboxStatus.RETRY_WAIT)), any(LocalDateTime.class)))
                .thenReturn(1);
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(outboxEvent));
        when(outboxEventPublisher.publish(outboxEvent))
                .thenReturn(OutboxPublishResult.failure("IllegalStateException", "broker unavailable"));

        int publishedCount = outboxPublisherService.publishReadyEvents(100);

        assertThat(publishedCount).isZero();
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.RETRY_WAIT);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getNextAttemptAt()).isNotNull();
        assertThat(outboxEvent.getFailureReason()).contains("broker unavailable");
        assertThat(meterRegistry.find("commerce.outbox.publish.failure")
                .tag("eventType", "NOTIFICATION")
                .tag("topic", "commerce.notification.requested")
                .tag("failureCode", "IllegalStateException")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    @Test
    void publishReadyEvents_marksDeadLetterAfterMaxRetryCount() {
        OutboxEvent outboxEvent = outboxEvent(1L, OutboxStatus.RETRY_WAIT);
        outboxEvent.markRetryWaiting("IllegalStateException", "first failure", LocalDateTime.now());

        when(outboxEventRepository.findPublishableEvents(anyList(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(outboxEvent));
        when(outboxEventRepository.claimPublishableEvent(eq(1L), eq(List.of(OutboxStatus.READY, OutboxStatus.RETRY_WAIT)), any(LocalDateTime.class)))
                .thenReturn(1);
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(outboxEvent));
        when(outboxEventPublisher.publish(outboxEvent))
                .thenReturn(OutboxPublishResult.failure("IllegalStateException", "still unavailable"));

        int publishedCount = outboxPublisherService.publishReadyEvents(100);

        assertThat(publishedCount).isZero();
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(2);
        assertThat(outboxEvent.getDeadLetteredAt()).isNotNull();
        assertThat(outboxEvent.getFailureReason()).contains("still unavailable");
        assertThat(counter("commerce.outbox.dead_letter.count")).isEqualTo(1.0);
    }

    @Test
    void publishReadyEvents_skipsAlreadyProcessingEventWhenClaimFails() {
        OutboxEvent outboxEvent = outboxEvent(1L, OutboxStatus.PROCESSING);

        when(outboxEventRepository.findPublishableEvents(anyList(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(outboxEvent));
        when(outboxEventRepository.claimPublishableEvent(eq(1L), eq(List.of(OutboxStatus.READY, OutboxStatus.RETRY_WAIT)), any(LocalDateTime.class)))
                .thenReturn(0);

        int publishedCount = outboxPublisherService.publishReadyEvents(100);

        assertThat(publishedCount).isZero();
        verify(outboxEventPublisher, never()).publish(ArgumentMatchers.any(OutboxEvent.class));
        assertThat(meterRegistry.find("commerce.outbox.publish.skipped")
                .tag("reason", "claim_failed")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    private OutboxEvent outboxEvent(Long id, OutboxStatus status) {
        OutboxEvent event = new OutboxEvent(1L, "commerce.notification.requested", "NOTIFICATION", "{}", status);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private double counter(String name) {
        return meterRegistry.find(name)
                .tag("eventType", "NOTIFICATION")
                .tag("topic", "commerce.notification.requested")
                .counter()
                .count();
    }
}
