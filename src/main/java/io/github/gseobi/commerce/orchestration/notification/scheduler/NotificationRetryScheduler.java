package io.github.gseobi.commerce.orchestration.notification.scheduler;

import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetrySchedulerTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "notification.retry.scheduler.enabled",
        havingValue = "true"
)
public class NotificationRetryScheduler {

    private final NotificationRetrySchedulerTrigger notificationRetryProcessor;

    @Scheduled(fixedDelayString = "${notification.retry.scheduler.fixed-delay-ms:60000}")
    public void processDueRetryEvents() {
        notificationRetryProcessor.processDueRetryEvents();
    }
}
