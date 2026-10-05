package io.github.gseobi.commerce.orchestration.merchant.api;

import java.util.Set;

public record MerchantAccessContext(Long merchantId, String actorId, Set<MerchantPermission> permissions) {

    public MerchantAccessContext {
        if (merchantId == null || merchantId <= 0 || actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("merchantId와 actorId가 필요합니다.");
        }
        permissions = Set.copyOf(permissions);
    }
}
