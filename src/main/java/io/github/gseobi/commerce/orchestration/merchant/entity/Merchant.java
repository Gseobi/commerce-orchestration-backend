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
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "merchants",
        uniqueConstraints = @UniqueConstraint(name = "uk_merchants_code", columnNames = "code")
)
public class Merchant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MerchantStatus status;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_recovery_policy", nullable = false, length = 40)
    private MerchantRecoveryPolicy defaultRecoveryPolicy;

    @Version
    private Long version;

    protected Merchant() {
    }

    public Merchant(String code, String name, String timezone) {
        this.code = normalizeCode(code, "사업자 코드");
        this.name = requireText(name, "사업자명", 120);
        this.timezone = validateTimezone(timezone);
        this.status = MerchantStatus.ACTIVE;
        this.defaultRecoveryPolicy = MerchantRecoveryPolicy.MANUAL_REVIEW;
    }

    public void suspend() {
        this.status = MerchantStatus.SUSPENDED;
    }

    public void activate() {
        this.status = MerchantStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == MerchantStatus.ACTIVE;
    }

    static String normalizeCode(String value, String label) {
        String normalized = requireText(value, label, 50).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9_-]*")) {
            throw new IllegalArgumentException(label + "는 영문 대문자, 숫자, 밑줄, 하이픈만 사용할 수 있습니다.");
        }
        return normalized;
    }

    static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "는 필수입니다.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "는 " + maxLength + "자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    private static String validateTimezone(String value) {
        String timezone = requireText(value, "timezone", 64);
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("유효하지 않은 timezone입니다: " + timezone, exception);
        }
    }
}
