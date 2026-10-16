package io.github.gseobi.commerce.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.order.api.OrderWorkflowAccess;
import io.github.gseobi.commerce.orchestration.order.api.TenantOrderQuery;
import io.github.gseobi.commerce.orchestration.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest
class TenantOrderQueryIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired private TenantOrderQuery query;
    @Autowired private MerchantApplication merchants;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderRepository legacyOrders;
    @Autowired private OrderWorkflowAccess legacyWorkflow;

    @Test
    void readsOwnedTenantSnapshotWithoutChangingRow() {
        Long owner = merchant("A");
        Long order = insertOrder(owner);
        var before = jdbc.queryForMap("select * from orders where id=?", order);

        var result = query.getOrder(owner, order);

        assertThat(result.merchantId()).isEqualTo(owner);
        assertThat(result.orderId()).isEqualTo(order);
        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.totalAmount()).isEqualByComparingTo("10.25");
        assertThat(result.currency()).isEqualTo("KRW");
        assertThat(jdbc.queryForMap("select * from orders where id=?", order)).isEqualTo(before);
    }

    @Test
    void foreignMissingOrderAndUnknownMerchantAreIndistinguishable() {
        Long first = merchant("A"), second = merchant("B");
        Long order = insertOrder(first);

        assertNotFound(second, order);
        assertNotFound(first, 999999L);
        assertNotFound(999999L, order);
        assertThat(query.getOrder(first, order).orderId()).isEqualTo(order);
    }

    @Test
    void tenantQueryNeverFallsBackToLegacyOrder() {
        Long owner = merchant("A");
        Long legacy = insertOrder(null);

        assertNotFound(owner, legacy);
        assertThat(legacyOrders.findById(legacy)).isPresent();
        assertThat(legacyWorkflow.getOrderExecutionView(legacy).orderId()).isEqualTo(legacy);
    }

    @Test
    void interleavedOwnersCannotReusePreviousReadContext() {
        Long first = merchant("A"), second = merchant("B");
        Long firstOrder = insertOrder(first), secondOrder = insertOrder(second);

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(query.getOrder(first, firstOrder).merchantId()).isEqualTo(first);
            assertNotFound(second, firstOrder);
            assertThat(query.getOrder(second, secondOrder).merchantId()).isEqualTo(second);
            assertNotFound(first, secondOrder);
        }
    }

    @Test
    void tenantReadDoesNotMakeLegacyWorkflowAbleToReadOrMutateTenant() {
        Long owner = merchant("A");
        Long order = insertOrder(owner);
        query.getOrder(owner, order);

        assertThat(legacyOrders.findById(order)).isEmpty();
        assertThatThrownBy(() -> legacyWorkflow.getOrderExecutionView(order))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThatThrownBy(() -> legacyWorkflow.markPaid(order))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThat(jdbc.queryForObject("select status from orders where id=?", String.class, order))
                .isEqualTo("CREATED");
    }

    private void assertNotFound(Long merchantId, Long orderId) {
        assertThatThrownBy(() -> query.getOrder(merchantId, orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ORDER_NOT_FOUND.getMessage())
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    private Long merchant(String code) {
        return merchants.registerMerchant(new MerchantRegistrationCommand(code, code, "Asia/Seoul")).id();
    }

    private Long insertOrder(Long merchantId) {
        // Trusted JDBC fixtures only; no tenant writer is enabled in the application.
        var fields = new LinkedHashMap<String, Object>();
        fields.put("customer_id", "private-customer-fixture");
        fields.put("description", "private-description-fixture");
        fields.put("total_amount", new BigDecimal("10.25"));
        fields.put("currency", "KRW");
        fields.put("status", "CREATED");
        fields.put("created_at", LocalDateTime.now());
        fields.put("updated_at", fields.get("created_at"));
        fields.put("ownership_mode", merchantId == null ? "LEGACY" : "TENANT");
        fields.put("merchant_id", merchantId);
        return new SimpleJdbcInsert(jdbc).withTableName("orders")
                .usingColumns(fields.keySet().toArray(String[]::new))
                .usingGeneratedKeyColumns("id").executeAndReturnKey(fields).longValue();
    }
}
