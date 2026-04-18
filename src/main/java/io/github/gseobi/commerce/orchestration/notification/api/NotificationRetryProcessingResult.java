package io.github.gseobi.commerce.orchestration.notification.api;

public record NotificationRetryProcessingResult(
        int scannedCount,
        int successCount,
        int rescheduledCount,
        int manualRequiredCount
) {
}
