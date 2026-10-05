package io.github.gseobi.commerce.orchestration.merchant.api;

public record MerchantRegistrationCommand(
        String code,
        String name,
        String timezone
) {
}
