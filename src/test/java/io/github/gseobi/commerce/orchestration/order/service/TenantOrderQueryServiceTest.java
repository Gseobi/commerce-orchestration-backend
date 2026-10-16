package io.github.gseobi.commerce.orchestration.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderView;
import io.github.gseobi.commerce.orchestration.order.repository.TenantOrderReadRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantOrderQueryServiceTest {

    @Mock private TenantOrderReadRepository repository;
    @InjectMocks private TenantOrderQueryService service;

    @Test
    void returnsImmutableSnapshotUsingBothIdentifiers() {
        var row = mock(TenantOrderReadRepository.Snapshot.class);
        when(row.getMerchantId()).thenReturn(11L);
        when(row.getOrderId()).thenReturn(22L);
        when(row.getStatus()).thenReturn("CREATED");
        when(row.getTotalAmount()).thenReturn(BigDecimal.TEN);
        when(row.getCurrency()).thenReturn("KRW");
        when(repository.findOwnedOrder(11L, 22L)).thenReturn(Optional.of(row));

        assertThat(service.getOrder(11L, 22L))
                .isEqualTo(new TenantOrderView(11L, 22L, "CREATED", BigDecimal.TEN, "KRW"));
        verify(repository).findOwnedOrder(11L, 22L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void absentAndInaccessibleRowsUseOrderNotFoundWithoutFallback() {
        when(repository.findOwnedOrder(11L, 22L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(11L, 22L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        verify(repository).findOwnedOrder(11L, 22L);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @MethodSource("invalidIdentifiers")
    void rejectsInvalidIdentifiersBeforeQuery(Long merchantId, Long orderId) {
        assertThatThrownBy(() -> service.getOrder(merchantId, orderId))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    private static Stream<Arguments> invalidIdentifiers() {
        return Stream.of(Arguments.of(null, 1L), Arguments.of(0L, 1L), Arguments.of(-1L, 1L),
                Arguments.of(1L, null), Arguments.of(1L, 0L), Arguments.of(1L, -1L));
    }
}
