package io.github.gseobi.commerce.orchestration.merchant.entity;

import io.github.gseobi.commerce.orchestration.merchant.api.MerchantPermission;
import java.util.Set;

public enum MembershipRole {
    VIEWER(Set.of(MerchantPermission.READ)),
    OPERATOR(Set.of(MerchantPermission.READ, MerchantPermission.WRITE));

    private final Set<MerchantPermission> permissions;

    MembershipRole(Set<MerchantPermission> permissions) {
        this.permissions = permissions;
    }

    public Set<MerchantPermission> permissions() {
        return permissions;
    }
}
