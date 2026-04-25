package io.github.gseobi.commerce.orchestration.notification.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
        when(notificationRetrySchedulerTrigger.processDueRetryEvents())
                .thenReturn(NotificationRetryProcessingResult.completed(3, 2, 1, 0, java.util.List.of(1L, 2L, 3L)));

        mockMvc.perform(post("/api/admin/notification-events/retry-due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.processedCount").value(3))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.processedEventIds[0]").value(1))
                .andExpect(jsonPath("$.processedEventIds[1]").value(2))
                .andExpect(jsonPath("$.processedEventIds[2]").value(3));

        verify(notificationRetrySchedulerTrigger).processDueRetryEvents();
    }
}
