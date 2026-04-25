package io.github.gseobi.commerce.orchestration.admin.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.repository.NotificationEventRepository;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.repository.OutboxEventRepository;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest
class AdminReprocessingIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private MockMvc mockMvc;
    private String adminAccessToken;
    private String userAccessToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        adminAccessToken = issueToken("admin-user", "[\"ROLE_USER\", \"ROLE_ADMIN\"]");
        userAccessToken = issueToken("normal-user", "[\"ROLE_USER\"]");
    }

    @TestConfiguration
    static class TestKafkaConfiguration {

        @Bean
        @Primary
        KafkaTemplate<String, String> kafkaTemplate() {
            KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
            when(kafkaTemplate.send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(CompletableFuture.completedFuture(
                            new SendResult<>(new ProducerRecord<>("test-topic", "key", "value"), null)
                    ));
            return kafkaTemplate;
        }
    }

    @Test
    void adminRetryNotification_completesFailedOrder() throws Exception {
        Long orderId = createOrder("FAIL_NOTIFICATION_RETRY");

        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("FAILED")));

        NotificationEvent notificationEvent = notificationEventRepository.findAllByOrderIdOrderByIdAsc(orderId).getFirst();

        mockMvc.perform(post("/api/admin/notification-events/{notificationEventId}/retry", notificationEvent.getId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId", is(notificationEvent.getId().intValue())))
                .andExpect(jsonPath("$.data.orderId", is(orderId.intValue())))
                .andExpect(jsonPath("$.data.action", is("RETRY")))
                .andExpect(jsonPath("$.data.result", is("SUCCESS")))
                .andExpect(jsonPath("$.data.previousStatus", is("RETRY_SCHEDULED")))
                .andExpect(jsonPath("$.data.currentStatus", is("SENT")))
                .andExpect(jsonPath("$.data.message", is("Notification event was retried successfully.")))
                .andExpect(jsonPath("$.data.orderStatus", is("COMPLETED")))
                .andExpect(jsonPath("$.data.handlingPolicy", is("NONE")));

        mockMvc.perform(post("/api/admin/notification-events/{notificationEventId}/retry", notificationEvent.getId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("ADMIN_REPROCESS_NOT_ALLOWED")));

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("COMPLETED")))
                .andExpect(jsonPath("$.data.notificationStatuses[0]", is("SENT")));
    }

    @Test
    void adminRetryOutboxDeadLetter_requiresAdminRole_andRepublishesEvent() throws Exception {
        Long orderId = createOrder("normal happy path");

        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk());

        OutboxEvent outboxEvent = outboxEventRepository.findAllByOrderIdOrderByIdAsc(orderId).getFirst();
        outboxEvent.markDeadLetter("TEST", "force dead letter in test");
        outboxEventRepository.save(outboxEvent);

        mockMvc.perform(post("/api/admin/outbox-events/{outboxEventId}/retry", outboxEvent.getId())
                        .header("Authorization", "Bearer " + userAccessToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/outbox-events/{outboxEventId}/retry", outboxEvent.getId())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.data.action", is("RETRY_OUTBOX_DEAD_LETTER")));
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
                                  "customerId": "customer-1",
                                  "totalAmount": 12000,
                                  "currency": "KRW",
                                  "description": "%s"
                                }
                                """.formatted(description)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("data").get("orderId").asLong();
    }
}
