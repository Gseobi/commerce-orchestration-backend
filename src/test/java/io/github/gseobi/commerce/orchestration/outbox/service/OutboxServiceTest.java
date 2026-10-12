package io.github.gseobi.commerce.orchestration.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gseobi.commerce.orchestration.outbox.api.OrderEventType;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class OutboxServiceTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxPublisherService publisher = mock(OutboxPublisherService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final OutboxService service = new OutboxService(repository, publisher, mapper);

    @ParameterizedTest
    @EnumSource(OrderEventType.class)
    void typedEventUsesCompatibleRouteAndJsonPayload(OrderEventType type) throws Exception {
        service.appendOrderEvent(42L, type);
        var captured = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captured.capture());
        var event = captured.getValue();
        assertThat(event.getOrderId()).isEqualTo(42L);
        assertThat(event.getTopic()).isEqualTo(type == OrderEventType.SETTLEMENT_REQUESTED
                ? "commerce.settlement.requested" : "commerce.notification.requested");
        assertThat(event.getEventType()).isEqualTo(type.name());
        assertThat(mapper.readTree(event.getPayload())).isEqualTo(mapper.readTree(
                "{\"orderId\":42,\"eventType\":\"" + type.name() + "\"}"));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.READY);
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void invalidOrderIdCannotPersistOrPublish(Long id) {
        assertThatThrownBy(() -> service.appendOrderEvent(id, OrderEventType.NOTIFICATION_REQUESTED))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void missingEventTypeCannotPersistOrPublish() {
        assertThatThrownBy(() -> service.appendOrderEvent(1L, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void serializationFailureCannotPersistPartialOutboxRow() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("fixture failure") { });
        var service = new OutboxService(repository, publisher, failing);
        assertThatThrownBy(() -> service.appendOrderEvent(1L, OrderEventType.SETTLEMENT_REQUESTED))
                .isInstanceOf(IllegalStateException.class).hasMessage("Order event serialization failed");
        verifyNoInteractions(repository, publisher);
    }
}
