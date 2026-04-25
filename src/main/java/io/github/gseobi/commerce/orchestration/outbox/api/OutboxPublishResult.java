package io.github.gseobi.commerce.orchestration.outbox.api;

public record OutboxPublishResult(
        boolean successful,
        String failureCode,
        String failureReason
) {

    public static OutboxPublishResult success() {
        return new OutboxPublishResult(true, null, null);
    }

    public static OutboxPublishResult failure(String failureCode, String failureReason) {
        return new OutboxPublishResult(false, failureCode, failureReason);
    }

    public boolean isSuccess() {
        return successful;
    }
}
