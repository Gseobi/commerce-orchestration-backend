package io.github.gseobi.commerce.orchestration.merchant.api;

public record StoreView(
        Long id,
        Long merchantId,
        String code,
        String name,
        String status,
        String timezone,
        String recoveryPolicy
) {
}
