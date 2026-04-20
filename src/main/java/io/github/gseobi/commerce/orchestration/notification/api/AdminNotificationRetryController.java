package io.github.gseobi.commerce.orchestration.notification.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notification-events")
public class AdminNotificationRetryController {

    private final NotificationRetrySchedulerTrigger notificationRetrySchedulerTrigger;

    @PostMapping("/retry-due")
    public NotificationRetryBatchResponse retryDueNotificationEvents() {
        notificationRetrySchedulerTrigger.processDueRetryEvents();
        return NotificationRetryBatchResponse.triggered();
    }

    public record NotificationRetryBatchResponse(String status) {

        public static NotificationRetryBatchResponse triggered() {
            return new NotificationRetryBatchResponse("triggered");
        }
    }
}
