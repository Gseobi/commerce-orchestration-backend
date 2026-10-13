package io.github.gseobi.commerce.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gseobi.commerce.orchestration.audit.repository.AuditLogRepository;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEventStatus;
import io.github.gseobi.commerce.orchestration.notification.repository.NotificationEventRepository;
import io.github.gseobi.commerce.orchestration.orchestration.repository.OrchestrationStepRepository;
import io.github.gseobi.commerce.orchestration.order.repository.OrderRepository;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import io.github.gseobi.commerce.orchestration.payment.repository.PaymentRepository;
import io.github.gseobi.commerce.orchestration.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest
class CommerceOwnershipMigrationIntegrationTest extends TestcontainersIntegrationSupport {
    private static final List<String> TABLES = List.of("orders", "payments", "settlements", "notification_events",
            "outbox_events", "orchestration_steps", "audit_logs");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MerchantApplication merchants;
    @Autowired private OrderRepository orders;
    @Autowired private PaymentRepository payments;
    @Autowired private SettlementRepository settlements;
    @Autowired private NotificationEventRepository notifications;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private OrchestrationStepRepository steps;
    @Autowired private AuditLogRepository audit;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void v8UpgradePreservesAllLegacyContentsIncludingOrphanAndNullAudit() {
        String schema = "ownership_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var dataSource = new DriverManagerDataSource(POSTGRESQL_CONTAINER.getJdbcUrl()
                + (POSTGRESQL_CONTAINER.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                POSTGRESQL_CONTAINER.getUsername(), POSTGRESQL_CONTAINER.getPassword());
        var isolated = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target("8").load().migrate();
            Long parent = insert(isolated, "orders", null, null, null);
            for (String table : TABLES.subList(1, TABLES.size())) insert(isolated, table, parent, null, null);
            insert(isolated, "audit_logs", null, null, null);
            insert(isolated, "notification_events", 999999L, null, null);
            Map<String, List<String>> before = snapshot(isolated);
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").load().migrate();
            assertThat(snapshot(isolated)).isEqualTo(before);
            for (String table : TABLES) {
                assertThat(isolated.queryForObject("select count(*) from " + table
                        + " where ownership_mode <> 'LEGACY' or merchant_id is not null", Long.class)).isZero();
            }
            assertThat(isolated.queryForObject("select count(*) from pg_constraint c join pg_namespace n"
                    + " on n.oid=c.connamespace where n.nspname=? and c.conname like '%_scope_order'"
                    + " and not c.convalidated", Long.class, schema)).isEqualTo(6);
            assertThatThrownBy(() -> insert(isolated, "notification_events", 999999L, "LEGACY", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            // Only the unique test schema is dropped; Compose/application data is never touched.
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"orders", "payments", "settlements", "notification_events", "outbox_events", "orchestration_steps", "audit_logs"})
    void tenantRequiresKnownOwnerAndLegacyCannotHaveOwner(String table) {
        Long merchant = merchant("A");
        Long parent = insert(jdbc, "orders", null, "TENANT", merchant);
        assertThatThrownBy(() -> insert(jdbc, table, parent, "TENANT", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(jdbc, table, parent, "TENANT", 999999L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(jdbc, table, parent, "LEGACY", merchant))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(jdbc, table, parent, "UNKNOWN", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payments", "settlements", "notification_events", "outbox_events", "orchestration_steps", "audit_logs"})
    void childCannotCrossOwnerOrScopeButValidTenantChildIsAccepted(String table) {
        Long first = merchant("A"), second = merchant("B");
        Long parent = insert(jdbc, "orders", null, "TENANT", first);
        Long legacy = insert(jdbc, "orders", null, "LEGACY", null);
        assertThatThrownBy(() -> insert(jdbc, table, parent, "TENANT", second))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(jdbc, table, parent, "LEGACY", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(jdbc, table, legacy, "TENANT", first))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(insert(jdbc, table, parent, "TENANT", first)).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"orders", "payments", "settlements", "notification_events", "outbox_events", "orchestration_steps", "audit_logs"})
    void legacyRepositoriesCannotReadTenantAndOwnerIsImmutable(String table) {
        Long owner = merchant("A"), other = merchant("B");
        Long parent = insert(jdbc, "orders", null, "TENANT", owner);
        Long id = table.equals("orders") ? parent : insert(jdbc, table, parent, "TENANT", owner);
        assertThat(repository(table).findById(id)).isEmpty();
        assertThat(repository(table).findAll()).isEmpty();
        assertThat(repository(table).count()).isZero();
        assertThatThrownBy(() -> jdbc.update("update " + table + " set merchant_id=? where id=?", other, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update " + table + " set ownership_mode='LEGACY', merchant_id=null where id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long legacyParent = insert(jdbc, "orders", null, "LEGACY", null);
        Long legacyId = table.equals("orders") ? legacyParent : insert(jdbc, table, legacyParent, "LEGACY", null);
        assertThat(repository(table).findById(legacyId)).isPresent();
        assertThatThrownBy(() -> jdbc.update("update " + table
                + " set ownership_mode='TENANT', merchant_id=? where id=?", owner, legacyId))
                .isInstanceOf(DataIntegrityViolationException.class).rootCause()
                .hasMessageContaining("Commerce ownership is immutable");
    }

    @Test
    void tenantAuditRequiresOrderButLegacyNonOrderAuditIsPreserved() {
        Long owner = merchant("A");
        assertThatThrownBy(() -> insert(jdbc, "audit_logs", null, "TENANT", owner))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(insert(jdbc, "audit_logs", null, "LEGACY", null)).isPositive();
    }

    @Test
    void legacyWorkerCandidateQueriesAndBulkClaimsExcludeTenant() {
        Long owner = merchant("A");
        Long parent = insert(jdbc, "orders", null, "TENANT", owner);
        Long event = insert(jdbc, "outbox_events", parent, "TENANT", owner);
        Long notification = insert(jdbc, "notification_events", parent, "TENANT", owner);
        jdbc.update("update notification_events set status='RETRY_SCHEDULED', next_attempt_at=? where id=?",
                LocalDateTime.now().minusMinutes(1), notification);
        var now = LocalDateTime.now();
        var statuses = List.of(OutboxStatus.READY, OutboxStatus.RETRY_WAIT);
        assertThat(outbox.findPublishableEvents(statuses, now, org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        assertThat(notifications.findDueRetryScheduledEvents(NotificationEventStatus.RETRY_SCHEDULED, now, 3,
                org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        var transaction = new TransactionTemplate(transactions);
        Integer outboxClaim = transaction.execute(status -> outbox.claimPublishableEvent(event, statuses, now));
        Integer notificationClaim = transaction.execute(status -> notifications.claimRetryScheduledEvent(notification, now, 3));
        assertThat(outboxClaim).isZero();
        assertThat(notificationClaim).isZero();
        assertThat(jdbc.queryForObject("select status from outbox_events where id=?", String.class, event)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("select status from notification_events where id=?", String.class, notification))
                .isEqualTo("RETRY_SCHEDULED");
    }

    private Long merchant(String code) {
        return merchants.registerMerchant(new MerchantRegistrationCommand(code, code, "Asia/Seoul")).id();
    }

    private JpaRepository<?, Long> repository(String table) {
        return switch (table) {
            case "orders" -> orders;
            case "payments" -> payments;
            case "settlements" -> settlements;
            case "notification_events" -> notifications;
            case "outbox_events" -> outbox;
            case "orchestration_steps" -> steps;
            case "audit_logs" -> audit;
            default -> throw new IllegalArgumentException("Unsupported test table");
        };
    }

    private Map<String, List<String>> snapshot(JdbcTemplate database) {
        var result = new LinkedHashMap<String, List<String>>();
        for (String table : TABLES) result.put(table, database.queryForList(
                "select (to_jsonb(t) - 'ownership_mode' - 'merchant_id')::text from " + table + " t order by id", String.class));
        return result;
    }

    private Long insert(JdbcTemplate database, String table, Long order, String mode, Long merchant) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Unsupported test table");
        var fields = new LinkedHashMap<String, Object>();
        fields.put("created_at", Timestamp.from(Instant.now()));
        fields.put("updated_at", fields.get("created_at"));
        if (!table.equals("orders")) fields.put("order_id", order);
        switch (table) {
            case "orders" -> {
                fields.put("customer_id", "fixture"); fields.put("total_amount", BigDecimal.TEN);
                fields.put("currency", "KRW"); fields.put("status", "CREATED");
            }
            case "payments" -> { fields.put("status", "APPROVED"); fields.put("amount", BigDecimal.TEN); }
            case "settlements" -> fields.put("status", "REQUESTED");
            case "notification_events" -> fields.put("status", "SENT");
            case "outbox_events" -> {
                fields.put("topic", "commerce.notification.requested"); fields.put("event_type", "NOTIFICATION_REQUESTED");
                fields.put("payload", "{}"); fields.put("status", "READY");
            }
            case "orchestration_steps" -> { fields.put("step_type", "ORDER_CREATE"); fields.put("status", "SUCCESS"); }
            case "audit_logs" -> { fields.put("action", "FIXTURE"); fields.put("detail", "fixture"); }
            default -> throw new IllegalArgumentException("Unsupported test table");
        }
        if (mode != null) { fields.put("ownership_mode", mode); fields.put("merchant_id", merchant); }
        return new SimpleJdbcInsert(database).withTableName(table).usingColumns(fields.keySet().toArray(String[]::new))
                .usingGeneratedKeyColumns("id").executeAndReturnKey(fields).longValue();
    }
}
