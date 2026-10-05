package io.github.gseobi.commerce.orchestration.merchant.api;

public interface MerchantAccessApplication {

    // actorId는 신뢰할 수 있는 identity provider의 subject여야 합니다.
    MerchantAccessContext authorize(Long merchantId, String actorId, MerchantPermission permission);
}
