package io.github.gseobi.commerce.orchestration.orchestration.api;

import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryProcessingResult;
import java.time.LocalDateTime;

public interface NotificationRetryProcessorApplication {

    NotificationRetryProcessingResult processDueRetries(LocalDateTime now, int limit);
}
