package io.github.gseobi.commerce.orchestration.admin.api;

public record AdminRecoveryContext(
        String operatorId,
        String reason
) {

    private static final int MAX_OPERATOR_ID_LENGTH = 100;
    private static final int MAX_REASON_LENGTH = 500;
    private static final String UNKNOWN_OPERATOR = "unknown";
    private static final String REASON_NOT_PROVIDED = "not-provided";

    public static AdminRecoveryContext defaults() {
        return new AdminRecoveryContext(UNKNOWN_OPERATOR, REASON_NOT_PROVIDED);
    }

    public static AdminRecoveryContext of(String operatorId, String reason) {
        return new AdminRecoveryContext(
                normalize(operatorId, UNKNOWN_OPERATOR, MAX_OPERATOR_ID_LENGTH),
                normalize(reason, REASON_NOT_PROVIDED, MAX_REASON_LENGTH)
        );
    }

    private static String normalize(String value, String fallback, int maxLength) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength
                ? trimmed.substring(0, maxLength)
                : trimmed;
    }
}
