package io.github.gseobi.commerce.orchestration.merchant.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MerchantTest {

    @Test
    void constructor_normalizesCodeAndAppliesSafeDefaults() {
        Merchant merchant = new Merchant(" small-shop ", " 작은 상점 ", "Asia/Seoul");

        assertThat(merchant.getCode()).isEqualTo("SMALL-SHOP");
        assertThat(merchant.getName()).isEqualTo("작은 상점");
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(merchant.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(merchant.getDefaultRecoveryPolicy()).isEqualTo(MerchantRecoveryPolicy.MANUAL_REVIEW);
    }

    @Test
    void constructor_rejectsInvalidTimezone() {
        assertThatThrownBy(() -> new Merchant("SHOP", "상점", "Korea/Invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timezone");
    }

    @Test
    void constructor_rejectsUnsafeCodeCharacters() {
        assertThatThrownBy(() -> new Merchant("shop code", "상점", "Asia/Seoul"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사업자 코드");
    }

    @Test
    void suspendAndActivate_areExplicitAndIdempotent() {
        Merchant merchant = new Merchant("SHOP", "상점", "Asia/Seoul");

        merchant.suspend();
        merchant.suspend();
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);

        merchant.activate();
        merchant.activate();
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
    }
}
