package io.github.gseobi.commerce.orchestration.order.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest
class OrderFlowIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        MvcResult tokenResult = mockMvc.perform(post("/api/auth/token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "tester",
                                  "roles": ["ROLE_USER", "ROLE_ADMIN"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        accessToken = root.get("data").get("accessToken").asText();
    }

    @TestConfiguration
    static class TestKafkaConfiguration {

        @Bean
        @Primary
        KafkaTemplate<String, String> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }
    }

    @Test
    void createOrder_requiresJwtAuthentication() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "customer-1",
                                  "totalAmount": 12000,
                                  "currency": "KRW",
                                  "description": "normal"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orchestrate_happyPath_and_duplicateCall_isIdempotent() throws Exception {
        Long orderId = createOrder("normal happy path");

        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.data.orderStatus", is("COMPLETED")))
                .andExpect(jsonPath("$.data.steps", hasSize(6)))
                .andExpect(jsonPath("$.data.outboxEvents", hasSize(2)));

        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("COMPLETED")))
                .andExpect(jsonPath("$.data.steps", hasSize(6)))
                .andExpect(jsonPath("$.data.outboxEvents", hasSize(2)));

        mockMvc.perform(get("/api/orders/{orderId}/flow", orderId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("COMPLETED")))
                .andExpect(jsonPath("$.data.outboxEvents", hasSize(2)));
    }

    @Test
    void orchestrate_settlementFailure_recordsCompensation() throws Exception {
        Long orderId = createOrder("FAIL_SETTLEMENT");

        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("CANCELLED")))
                .andExpect(jsonPath("$.data.steps[3].stepType", is("SETTLEMENT")))
                .andExpect(jsonPath("$.data.steps[3].status", is("FAILED")))
                .andExpect(jsonPath("$.data.steps[4].stepType", is("COMPENSATION")))
                .andExpect(jsonPath("$.data.steps[4].status", is("SUCCESS")));
    }

    @Test
    void orchestrate_notificationFailure_recordsCompensationTodo() throws Exception {
        Long orderId = createOrder("FAIL_NOTIFICATION");

        mockMvc.perform(post("/api/orders/{orderId}/orchestrate", orderId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("FAILED")))
                .andExpect(jsonPath("$.data.steps[4].stepType", is("NOTIFICATION")))
                .andExpect(jsonPath("$.data.steps[4].status", is("FAILED")))
                .andExpect(jsonPath("$.data.steps[5].stepType", is("COMPENSATION")))
                .andExpect(jsonPath("$.data.steps[5].status", is("READY")));
    }

    @Test
    void createOrder_and_flow_endpoint_work_withJwt() throws Exception {
        Long orderId = createOrder("flow check");

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("CREATED")));

        mockMvc.perform(get("/api/orders/{orderId}/flow", orderId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("CREATED")))
                .andExpect(jsonPath("$.data.steps", hasSize(0)));
    }

    private Long createOrder(String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
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
