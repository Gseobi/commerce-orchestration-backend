package io.github.gseobi.commerce.orchestration.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class LegacyCommerceEntity extends BaseTimeEntity {
    @Column(name = "ownership_mode", nullable = false, length = 10, updatable = false)
    private String ownershipMode = "LEGACY";

    @Column(name = "merchant_id", updatable = false)
    private Long merchantId;
}
