package io.github.gseobi.commerce.orchestration.notification.api;

import java.util.List;

public record NotificationRetryProcessingResult(
        String status,
        int processedCount,
        int successCount,
        int failedCount,
        int skippedCount,
        List<Long> processedEventIds
) {

    public static NotificationRetryProcessingResult completed(
            int processedCount,
            int successCount,
            int failedCount,
            int skippedCount,
            List<Long> processedEventIds
    ) {
        return new NotificationRetryProcessingResult(
                "completed",
                processedCount,
                successCount,
                failedCount,
                skippedCount,
                List.copyOf(processedEventIds)
        );
    }
}
