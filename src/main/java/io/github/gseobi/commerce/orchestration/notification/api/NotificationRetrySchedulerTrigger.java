package io.github.gseobi.commerce.orchestration.notification.api;

public interface NotificationRetrySchedulerTrigger {

    NotificationRetryProcessingResult processDueRetryEvents();
}
