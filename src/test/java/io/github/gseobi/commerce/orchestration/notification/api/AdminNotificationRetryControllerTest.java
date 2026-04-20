package io.github.gseobi.commerce.orchestration.notification.api;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gseobi.commerce.orchestration.security.JwtAuthenticationFilter;
import io.github.gseobi.commerce.orchestration.security.JwtTokenProvider;
import io.github.gseobi.commerce.orchestration.security.RequestTraceFilter;
import io.github.gseobi.commerce.orchestration.security.RestAuthenticationEntryPoint;
import io.github.gseobi.commerce.orchestration.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminNotificationRetryController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RequestTraceFilter.class,
        RestAuthenticationEntryPoint.class
})
class AdminNotificationRetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationRetrySchedulerTrigger notificationRetrySchedulerTrigger;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "ADMIN")
    void retryDueNotificationEventsTriggersRetryBatch() throws Exception {
        mockMvc.perform(post("/api/admin/notification-events/retry-due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("triggered"));

        verify(notificationRetrySchedulerTrigger).processDueRetryEvents();
    }
}
