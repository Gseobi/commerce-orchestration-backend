package io.github.gseobi.commerce.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gseobi.commerce.orchestration.audit.entity.AuditLog;
import io.github.gseobi.commerce.orchestration.audit.repository.AuditLogRepository;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryProcessingResult;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEventStatus;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationHandlingPolicy;
import io.github.gseobi.commerce.orchestration.notification.repository.NotificationEventRepository;
import io.github.gseobi.commerce.orchestration.orchestration.api.NotificationRetryProcessorApplication;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.order.entity.OrderStatus;
import io.github.gseobi.commerce.orchestration.order.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest
class NotificationRetryProcessorIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private NotificationRetryProcessorApplication notificationRetryProcessorApplication;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private String adminAccessToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        adminAccessToken = issueToken("retry-processor-admin", "[\"ROLE_USER\", \"ROLE_ADMIN\"]");
    }

    @Test
    void processDueRetries_sendsDueRetryEventAndCompletesOrder() throws Exception {
        Long orderId = createOrder("FAIL_NOTIFICATION_RETRY processor-success");
        orchestrate(orderId);

        NotificationEvent scheduledEvent = getSingleNotificationEvent(orderId);
        NotificationRetryProcessingResult result = notificationRetryProcessorApplication.processDueRetries(
                scheduledEvent.getNextAttemptAt(),
                10
        );

        NotificationEvent retriedEvent = getSingleNotificationEvent(orderId);
        Order order = getOrder(orderId);
        List<AuditLog> audits = auditLogRepository.findAllByOrderIdOrderByIdAsc(orderId);

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(result.processedEventIds()).containsExactly(scheduledEvent.getId());
        assertThat(retriedEvent.getStatus().name()).isEqualTo("SENT");
        assertThat(retriedEvent.getHandlingPolicy()).isEqualTo(NotificationHandlingPolicy.NONE);
        assertThat(retriedEvent.getRetryCount()).isEqualTo(2);
        assertThat(retriedEvent.getNextAttemptAt()).isNull();
        assertThat(retriedEvent.getFailureCode()).isNull();
        assertThat(retriedEvent.getFailureReason()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(audits)
                .extracting(AuditLog::getAction)
                .contains("NOTIFICATION_RETRY_PROCESSED_SUCCESS");
    }

    @Test
    void processDueRetries_skipsFutureRetryEvent() throws Exception {
        Long orderId = createOrder("FAIL_NOTIFICATION_RETRY processor-future");
        orchestrate(orderId);

        NotificationEvent scheduledEvent = getSingleNotificationEvent(orderId);
        LocalDateTime beforeDue = scheduledEvent.getNextAttemptAt().minusSeconds(1);

        NotificationRetryProcessingResult result = notificationRetryProcessorApplication.processDueRetries(beforeDue, 10);

        NotificationEvent untouchedEvent = getSingleNotificationEvent(orderId);
        Order order = getOrder(orderId);

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.processedCount()).isZero();
        assertThat(result.successCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(result.processedEventIds()).isEmpty();
        assertThat(untouchedEvent.getStatus().name()).isEqualTo("RETRY_SCHEDULED");
        assertThat(untouchedEvent.getHandlingPolicy()).isEqualTo(NotificationHandlingPolicy.AUTO_RETRY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void processDueRetries_movesToManualInterventionWhenMaxRetryExceeded() throws Exception {
        Long orderId = createOrder("FAIL_NOTIFICATION_RETRY_PERSISTENT processor-exhausted");
        orchestrate(orderId);

        NotificationEvent firstScheduledEvent = getSingleNotificationEvent(orderId);
        NotificationRetryProcessingResult firstAttempt = notificationRetryProcessorApplication.processDueRetries(
                firstScheduledEvent.getNextAttemptAt(),
                10
        );

        NotificationEvent rescheduledEvent = getSingleNotificationEvent(orderId);
        NotificationRetryProcessingResult secondAttempt = notificationRetryProcessorApplication.processDueRetries(
                rescheduledEvent.getNextAttemptAt(),
                10
        );

        NotificationEvent manualEvent = getSingleNotificationEvent(orderId);
        Order order = getOrder(orderId);
        List<AuditLog> audits = auditLogRepository.findAllByOrderIdOrderByIdAsc(orderId);

        assertThat(firstAttempt.status()).isEqualTo("completed");
        assertThat(firstAttempt.processedCount()).isEqualTo(1);
        assertThat(firstAttempt.successCount()).isZero();
        assertThat(firstAttempt.failedCount()).isEqualTo(1);
        assertThat(firstAttempt.skippedCount()).isZero();
        assertThat(firstAttempt.processedEventIds()).containsExactly(firstScheduledEvent.getId());

        assertThat(secondAttempt.status()).isEqualTo("completed");
        assertThat(secondAttempt.processedCount()).isEqualTo(1);
        assertThat(secondAttempt.successCount()).isZero();
        assertThat(secondAttempt.failedCount()).isEqualTo(1);
        assertThat(secondAttempt.skippedCount()).isZero();
        assertThat(secondAttempt.processedEventIds()).containsExactly(rescheduledEvent.getId());

        assertThat(manualEvent.getStatus().name()).isEqualTo("MANUAL_INTERVENTION_REQUIRED");
        assertThat(manualEvent.getHandlingPolicy()).isEqualTo(NotificationHandlingPolicy.MANUAL_INTERVENTION);
        assertThat(manualEvent.getRetryCount()).isEqualTo(3);
        assertThat(manualEvent.getNextAttemptAt()).isNull();
        assertThat(manualEvent.getFailureCode()).isEqualTo("NOTIFICATION_RETRY_EXHAUSTED");
        assertThat(manualEvent.getFailureReason()).isNotBlank();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(audits)
                .extracting(AuditLog::getAction)
                .contains("NOTIFICATION_RETRY_RESCHEDULED", "NOTIFICATION_RETRY_MANUAL_INTERVENTION_REQUIRED");
    }

    @Test
    void retryDueNotificationEvents_returnsBatchResultSummary() throws Exception {
        LocalDateTime batchRunAt = LocalDateTime.now();

        Long successOrderId = createOrder("FAIL_NOTIFICATION_RETRY batch-success");
        orchestrate(successOrderId);
        NotificationEvent successEvent = getSingleNotificationEvent(successOrderId);
        makeRetryDue(successEvent.getId(), batchRunAt);

        Long failedOrderId = createOrder("FAIL_NOTIFICATION_RETRY_PERSISTENT batch-failed");
        orchestrate(failedOrderId);
        NotificationEvent failedEvent = getSingleNotificationEvent(failedOrderId);
        makeRetryDue(failedEvent.getId(), batchRunAt);

        Long futureOrderId = createOrder("FAIL_NOTIFICATION_RETRY batch-future");
        orchestrate(futureOrderId);
        NotificationEvent futureEvent = getSingleNotificationEvent(futureOrderId);
        makeRetryFuture(futureEvent.getId(), batchRunAt);

        assertThat(findDueRetryEventIds(batchRunAt))
                .containsExactly(successEvent.getId(), failedEvent.getId());

        MvcResult result = mockMvc.perform(post("/api/admin/notification-events/retry-due")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(root.get("status").asText()).isEqualTo("completed");
        assertThat(root.get("processedCount").asInt()).isEqualTo(2);
        assertThat(root.get("successCount").asInt()).isEqualTo(1);
        assertThat(root.get("failedCount").asInt()).isEqualTo(1);
        assertThat(root.get("skippedCount").asInt()).isEqualTo(0);
        assertThat(StreamSupport.stream(root.withArray("processedEventIds").spliterator(), false)
                .map(JsonNode::asLong)
                .toList())
                .containsExactly(successEvent.getId(), failedEvent.getId());

        NotificationEvent processedSuccessEvent = notificationEventRepository.findById(successEvent.getId()).orElseThrow();
        NotificationEvent processedFailedEvent = notificationEventRepository.findById(failedEvent.getId()).orElseThrow();
        NotificationEvent untouchedFutureEvent = notificationEventRepository.findById(futureEvent.getId()).orElseThrow();

        assertThat(processedSuccessEvent.getStatus().name()).isEqualTo("SENT");
        assertThat(processedFailedEvent.getStatus().name()).isEqualTo("RETRY_SCHEDULED");
        assertThat(processedFailedEvent.getRetryCount()).isEqualTo(failedEvent.getRetryCount() + 1);
        assertThat(processedFailedEvent.getNextAttemptAt()).isAfter(failedEvent.getNextAttemptAt());
        assertThat(untouchedFutureEvent.getStatus().name()).isEqualTo("RETRY_SCHEDULED");
        assertThat(untouchedFutureEvent.getRetryCount()).isEqualTo(futureEvent.getRetryCount());
        assertThat(untouchedFutureEvent.getLastAttemptAt()).isEqualTo(futureEvent.getLastAttemptAt());
        assertThat(untouchedFutureEvent.getNextAttemptAt()).isEqualTo(futureEvent.getNextAttemptAt());
    }

    private String issueToken(String username, String rolesJson) throws Exception {
        MvcResult tokenResult = mockMvc.perform(post("/api/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "roles": %s
                                }
                                """.formatted(username, rolesJson)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        return root.get("data").get("accessToken").asText();
    }

    private Long createOrder(String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminAccessToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "customer-notification-retry",
                                  "totalAmount": 15000,
                                  "currency": "KRW",
                                  "description": "%s"
                                }
                                """.formatted(description)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("data").get("orderId").asLong();
    }

    private void orchestrate(Long orderId) throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk());
    }

    private NotificationEvent getSingleNotificationEvent(Long orderId) {
        List<NotificationEvent> events = notificationEventRepository.findAllByOrderIdOrderByIdAsc(orderId);
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private void makeRetryDue(Long notificationEventId, LocalDateTime batchRunAt) {
        updateNextAttemptAt(notificationEventId, batchRunAt.minusMinutes(10));
    }

    private void makeRetryFuture(Long notificationEventId, LocalDateTime batchRunAt) {
        updateNextAttemptAt(notificationEventId, batchRunAt.plusMinutes(10));
    }

    private List<Long> findDueRetryEventIds(LocalDateTime batchRunAt) {
        return notificationEventRepository.findDueRetryScheduledEvents(
                        NotificationEventStatus.RETRY_SCHEDULED,
                        batchRunAt,
                        3,
                        PageRequest.of(0, 10)
                ).stream()
                .map(NotificationEvent::getId)
                .toList();
    }

    private void updateNextAttemptAt(Long notificationEventId, LocalDateTime nextAttemptAt) {
        int updatedRows = jdbcTemplate.update(
                "update notification_events set next_attempt_at = ? where id = ?",
                nextAttemptAt,
                notificationEventId
        );
        assertThat(updatedRows).isEqualTo(1);
    }
}
