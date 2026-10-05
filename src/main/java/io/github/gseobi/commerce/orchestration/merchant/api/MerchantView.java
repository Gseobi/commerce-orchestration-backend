package io.github.gseobi.commerce.orchestration.merchant.api;

public record MerchantView(
        Long id,
        String code,
        String name,
        String status,
        String timezone,
        String defaultRecoveryPolicy
) {
}
