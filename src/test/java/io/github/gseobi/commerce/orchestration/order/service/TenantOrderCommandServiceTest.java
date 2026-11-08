package io.github.gseobi.commerce.orchestration.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.merchant.api.OrderStoreAccess;
import io.github.gseobi.commerce.orchestration.merchant.api.OrderStoreAccess.OrderStoreSnapshot;
import io.github.gseobi.commerce.orchestration.order.api.CreateTenantOrderCommand;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderView;
import io.github.gseobi.commerce.orchestration.order.repository.TenantOrderWriteRepository;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantOrderCommandServiceTest {

    @Mock private OrderStoreAccess stores;
    @Mock private TenantOrderWriteRepository orders;
    @Mock private TenantOrderQuery query;
    @InjectMocks private TenantOrderCommandService service;

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void rejectsInvalidCommandBeforeStoreOrOrderAccess(CreateTenantOrderCommand command) {
        assertThatThrownBy(() -> service.create(1L, command)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(stores, orders, query);
    }

    @Test
    void rejectsInvalidIdentifiersBeforeAccess() {
        assertThatThrownBy(() -> service.create(null, command(BigDecimal.ONE))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.cancelCreated(1L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.cancelCreated(-1L, 1L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(stores, orders, query);
    }

    @Test
    void passesExactAmountAndAuthoritativeSnapshotToRepository() {
        var amount = new BigDecimal("9999999999999999.99");
        var command = command(amount);
        var expected = new TenantOrderView(1L, 3L, "CREATED", amount, "KRW");
        when(stores.lockActiveStore(1L, 2L)).thenReturn(new OrderStoreSnapshot("UTC", "MANUAL_REVIEW"));
        when(orders.insert(1L, command, "UTC", "MANUAL_REVIEW")).thenReturn(3L);
        when(query.getOrder(1L, 3L)).thenReturn(expected);

        assertThat(service.create(1L, command)).isEqualTo(expected);
        assertThat(command.toString()).doesNotContain(command.customerId());
    }

    private static Stream<CreateTenantOrderCommand> invalidCommands() {
        return Stream.of(null, command(null), command(BigDecimal.ZERO), command(new BigDecimal("0.001")),
                command(new BigDecimal("10000000000000000")), command(new BigDecimal("1E+2147483647")),
                new CreateTenantOrderCommand(0L, "customer-fixture", BigDecimal.ONE, "KRW"),
                new CreateTenantOrderCommand(2L, " ", BigDecimal.ONE, "KRW"),
                new CreateTenantOrderCommand(2L, "x".repeat(101), BigDecimal.ONE, "KRW"),
                new CreateTenantOrderCommand(2L, "customer-fixture", BigDecimal.ONE, "krw"));
    }

    private static CreateTenantOrderCommand command(BigDecimal amount) {
        return new CreateTenantOrderCommand(2L, "customer-fixture", amount, "KRW");
    }
}
