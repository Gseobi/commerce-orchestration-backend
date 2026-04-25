package io.github.gseobi.commerce.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gseobi.commerce.orchestration.audit.entity.AuditLog;
import io.github.gseobi.commerce.orchestration.audit.repository.AuditLogRepository;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationHandlingPolicy;
import io.github.gseobi.commerce.orchestration.notification.repository.NotificationEventRepository;
import io.github.gseobi.commerce.orchestration.orchestration.entity.OrchestrationStep;
import io.github.gseobi.commerce.orchestration.orchestration.repository.OrchestrationStepRepository;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.order.entity.OrderStatus;
import io.github.gseobi.commerce.orchestration.order.repository.OrderRepository;
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
class NotificationRecoveryIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private OrchestrationStepRepository orchestrationStepRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;
    private String adminAccessToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        adminAccessToken = issueToken("integration-admin", "[\"ROLE_USER\", \"ROLE_ADMIN\"]");
    }

    @Test
    void notificationIgnorePolicy_completesOrderWithoutRollback() throws Exception {
        Long orderId = createOrder("FAIL_NOTIFICATION_IGNORE");

        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"));

        NotificationEvent notificationEvent = getSingleNotificationEvent(orderId);
        Order order = getOrder(orderId);
        List<OrchestrationStep> steps = orchestrationStepRepository.findAllByOrderIdOrderByIdAsc(orderId);

        assertThat(notificationEvent.getStatus().name()).isEqualTo("IGNORED");
        assertThat(notificationEvent.getHandlingPolicy()).isEqualTo(NotificationHandlingPolicy.IGNORE);
        assertThat(notificationEvent.getFailureCode()).isEqualTo("NOTIFICATION_IGNORE_ALLOWED");
        assertThat(notificationEvent.getFailureReason()).isNotBlank();
        assertThat(notificationEvent.getNextAttemptAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(steps)
                .extracting(step -> step.getStepType().name() + ":" + step.getStatus().name())
                .contains("NOTIFICATION:SUCCESS", "COMPLETE:SUCCESS")
                .doesNotContain("COMPENSATION:READY");
    }

    @Test
    void notificationRetryPolicy_schedulesRetryAndAllowsAdminRecovery() throws Exception {
        Long orderId = createOrder("FAIL_NOTIFICATION_RETRY");

        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("FAILED"));

        NotificationEvent notificationEvent = getSingleNotificationEvent(orderId);
        Order failedOrder = getOrder(orderId);
        List<OrchestrationStep> failedSteps = orchestrationStepRepository.findAllByOrderIdOrderByIdAsc(orderId);
        List<AuditLog> failedAudits = auditLogRepository.findAllByOrderIdOrderByIdAsc(orderId);

        assertThat(notificationEvent.getStatus().name()).isEqualTo("RETRY_SCHEDULED");
        assertThat(notificationEvent.getHandlingPolicy()).isEqualTo(NotificationHandlingPolicy.AUTO_RETRY);
        assertThat(notificationEvent.getRetryCount()).isEqualTo(1);
        assertThat(notificationEvent.getNextAttemptAt()).isNotNull();
        assertThat(notificationEvent.getLastAttemptAt()).isNotNull();
        assertThat(notificationEvent.getFailureCode()).isEqualTo("NOTIFICATION_TRANSIENT_FAILURE");
        assertThat(notificationEvent.getFailureReason()).isNotBlank();
        assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(failedSteps.getLast().getStepType().name()).isEqualTo("COMPENSATION");
        assertThat(failedSteps.getLast().getStatus().name()).isEqualTo("READY");
        assertThat(failedSteps.getLast().getDetail()).contains("retry scheduled");
        assertThat(failedAudits)
                .extracting(AuditLog::getAction)
                .contains("NOTIFICATION_FAILED_RETRY_SCHEDULED");

        mockMvc.perform(post("/api/admin/notification-events/{notificationEventId}/retry", notificationEvent.getId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(notificationEvent.getId()))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.action").value("RETRY"))
                .andExpect(jsonPath("$.data.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.previousStatus").value("RETRY_SCHEDULED"))
                .andExpect(jsonPath("$.data.currentStatus").value("SENT"))
                .andExpect(jsonPath("$.data.message").value("Notification event was retried successfully."))
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.handlingPolicy").value("NONE"));

        NotificationEvent recoveredEvent = getSingleNotificationEvent(orderId);
        Order recoveredOrder = getOrder(orderId);
        List<AuditLog> recoveredAudits = auditLogRepository.findAllByOrderIdOrderByIdAsc(orderId);

        assertThat(recoveredEvent.getStatus().name()).isEqualTo("SENT");
        assertThat(recoveredEvent.getHandlingPolicy()).isEqualTo(NotificationHandlingPolicy.NONE);
        assertThat(recoveredEvent.getRetryCount()).isEqualTo(2);
        assertThat(recoveredEvent.getNextAttemptAt()).isNull();
        assertThat(recoveredEvent.getFailureCode()).isNull();
        assertThat(recoveredEvent.getFailureReason()).isNull();
        assertThat(recoveredOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(recoveredAudits)
                .extracting(AuditLog::getAction)
                .contains("ADMIN_NOTIFICATION_RETRIED");
    }

    @Test
    void notificationManualPolicy_requiresManualInterventionAndAllowsAdminIgnore() throws Exception {
        Long orderId = createOrder("FAIL_NOTIFICATION_MANUAL");

        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("FAILED"));

        NotificationEvent notificationEvent = getSingleNotificationEvent(orderId);
        Order failedOrder = getOrder(orderId);
        List<OrchestrationStep> failedSteps = orchestrationStepRepository.findAllByOrderIdOrderByIdAsc(orderId);
        List<AuditLog> failedAudits = auditLogRepository.findAllByOrderIdOrderByIdAsc(orderId);

        assertThat(notificationEvent.getStatus().name()).isEqualTo("MANUAL_INTERVENTION_REQUIRED");
        assertThat(notificationEvent.getHandlingPolicy()).isEqualTo(NotificationHandlingPolicy.MANUAL_INTERVENTION);
        assertThat(notificationEvent.getRetryCount()).isEqualTo(1);
        assertThat(notificationEvent.getNextAttemptAt()).isNull();
        assertThat(notificationEvent.getLastAttemptAt()).isNotNull();
        assertThat(notificationEvent.getFailureCode()).isEqualTo("NOTIFICATION_MANUAL_INTERVENTION_REQUIRED");
        assertThat(notificationEvent.getFailureReason()).isNotBlank();
        assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(failedSteps.getLast().getStepType().name()).isEqualTo("COMPENSATION");
        assertThat(failedSteps.getLast().getStatus().name()).isEqualTo("READY");
        assertThat(failedSteps.getLast().getDetail()).contains("manual intervention");
        assertThat(failedAudits)
                .extracting(AuditLog::getAction)
                .contains("NOTIFICATION_FAILED_MANUAL_INTERVENTION_REQUIRED");

        mockMvc.perform(post("/api/admin/notification-events/{notificationEventId}/ignore", notificationEvent.getId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(notificationEvent.getId()))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.action").value("IGNORE"))
                .andExpect(jsonPath("$.data.result").value("IGNORED"))
                .andExpect(jsonPath("$.data.previousStatus").value("MANUAL_INTERVENTION_REQUIRED"))
                .andExpect(jsonPath("$.data.currentStatus").value("IGNORED"))
                .andExpect(jsonPath("$.data.message").value("Notification event was marked as ignored."))
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.handlingPolicy").value("IGNORE"));

        NotificationEvent recoveredEvent = getSingleNotificationEvent(orderId);
        Order recoveredOrder = getOrder(orderId);
        List<AuditLog> recoveredAudits = auditLogRepository.findAllByOrderIdOrderByIdAsc(orderId);

        assertThat(recoveredEvent.getStatus().name()).isEqualTo("IGNORED");
        assertThat(recoveredEvent.getHandlingPolicy()).isEqualTo(NotificationHandlingPolicy.IGNORE);
        assertThat(recoveredOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(recoveredAudits)
                .extracting(AuditLog::getAction)
                .contains("ADMIN_NOTIFICATION_IGNORED");
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
                                  "customerId": "customer-notification",
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

    private NotificationEvent getSingleNotificationEvent(Long orderId) {
        List<NotificationEvent> events = notificationEventRepository.findAllByOrderIdOrderByIdAsc(orderId);
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }
}
