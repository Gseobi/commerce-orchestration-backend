package io.github.gseobi.commerce.orchestration.merchant.entity;

import io.github.gseobi.commerce.orchestration.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.Objects;
import lombok.Getter;

@Getter
@Entity
@Table(name = "merchant_memberships", uniqueConstraints = @UniqueConstraint(
        name = "uk_memberships_merchant_actor", columnNames = {"merchant_id", "actor_id"}
))
public class MerchantMembership extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "actor_id", nullable = false, length = 120)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MembershipRole role;

    @Column(nullable = false)
    private boolean active;

    @Version
    private Long version;

    protected MerchantMembership() {
    }

    public MerchantMembership(Long merchantId, String actorId, MembershipRole role) {
        if (merchantId == null || merchantId <= 0) {
            throw new IllegalArgumentException("양수 merchantId가 필요합니다.");
        }
        if (actorId == null || actorId.isBlank() || actorId.length() > 120
                || !actorId.equals(actorId.trim())) {
            throw new IllegalArgumentException("유효한 actorId가 필요합니다.");
        }
        this.merchantId = merchantId;
        this.actorId = actorId;
        this.role = Objects.requireNonNull(role, "role");
        this.active = true;
    }

    public void revoke() {
        this.active = false;
    }
}
