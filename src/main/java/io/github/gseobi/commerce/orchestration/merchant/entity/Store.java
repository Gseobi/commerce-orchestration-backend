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
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "stores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stores_merchant_code",
                columnNames = {"merchant_id", "code"}
        )
)
public class Store extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StoreStatus status;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_policy", nullable = false, length = 40)
    private MerchantRecoveryPolicy recoveryPolicy;

    @Version
    private Long version;

    protected Store() {
    }

    public Store(Merchant merchant, String code, String name) {
        if (merchant == null || merchant.getId() == null) {
            throw new IllegalArgumentException("영속화된 사업자가 필요합니다.");
        }
        if (!merchant.isActive()) {
            throw new IllegalStateException("활성 사업자에만 스토어를 등록할 수 있습니다.");
        }
        this.merchantId = merchant.getId();
        this.code = Merchant.normalizeCode(code, "스토어 코드");
        this.name = Merchant.requireText(name, "스토어명", 120);
        this.status = StoreStatus.ACTIVE;
        this.timezone = merchant.getTimezone();
        this.recoveryPolicy = merchant.getDefaultRecoveryPolicy();
    }

    public void deactivate() {
        this.status = StoreStatus.INACTIVE;
    }

    public void activate() {
        this.status = StoreStatus.ACTIVE;
    }
}
