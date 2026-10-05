package io.github.gseobi.commerce.orchestration.merchant.api;

public record StoreRegistrationCommand(
        Long merchantId,
        String code,
        String name
) {
}
