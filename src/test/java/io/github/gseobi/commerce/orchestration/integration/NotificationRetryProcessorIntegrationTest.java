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
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationHandlingPolicy;
import io.github.gseobi.commerce.orchestration.notification.repository.NotificationEventRepository;
import io.github.gseobi.commerce.orchestration.orchestration.api.NotificationRetryProcessorApplication;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.order.entity.OrderStatus;
import io.github.gseobi.commerce.orchestration.order.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.rescheduledCount()).isZero();
        assertThat(result.manualRequiredCount()).isZero();
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

        assertThat(result.scannedCount()).isZero();
        assertThat(result.successCount()).isZero();
        assertThat(result.rescheduledCount()).isZero();
        assertThat(result.manualRequiredCount()).isZero();
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

        assertThat(firstAttempt.scannedCount()).isEqualTo(1);
        assertThat(firstAttempt.successCount()).isZero();
        assertThat(firstAttempt.rescheduledCount()).isEqualTo(1);
        assertThat(firstAttempt.manualRequiredCount()).isZero();

        assertThat(secondAttempt.scannedCount()).isEqualTo(1);
        assertThat(secondAttempt.successCount()).isZero();
        assertThat(secondAttempt.rescheduledCount()).isZero();
        assertThat(secondAttempt.manualRequiredCount()).isEqualTo(1);

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
}
