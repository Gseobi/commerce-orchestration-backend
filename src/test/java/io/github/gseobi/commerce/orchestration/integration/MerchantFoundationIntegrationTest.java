package io.github.gseobi.commerce.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantView;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreView;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("integration-test")
@Tag("integration")
class MerchantFoundationIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired
    private MerchantApplication merchantApplication;

    @Test
    void registerMerchantAndStore_persistsOperationalDefaults() {
        MerchantView merchant = merchantApplication.registerMerchant(
                new MerchantRegistrationCommand("small-shop", "작은 상점", "Asia/Seoul")
        );

        StoreView store = merchantApplication.registerStore(
                new StoreRegistrationCommand(merchant.id(), "online", "온라인 스토어")
        );

        assertThat(merchantApplication.getMerchant(merchant.id())).isEqualTo(merchant);
        assertThat(merchantApplication.getStores(merchant.id()))
                .extracting(StoreView::id)
                .containsExactly(store.id());
        assertThat(store.timezone()).isEqualTo("Asia/Seoul");
        assertThat(store.recoveryPolicy()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void storeLookup_doesNotLeakAcrossMerchants() {
        MerchantView firstMerchant = merchantApplication.registerMerchant(
                new MerchantRegistrationCommand("FIRST", "첫 번째 상점", "Asia/Seoul")
        );
        MerchantView secondMerchant = merchantApplication.registerMerchant(
                new MerchantRegistrationCommand("SECOND", "두 번째 상점", "Asia/Seoul")
        );
        StoreView firstStore = merchantApplication.registerStore(
                new StoreRegistrationCommand(firstMerchant.id(), "ONLINE", "첫 번째 온라인")
        );
        StoreView secondStore = merchantApplication.registerStore(
                new StoreRegistrationCommand(secondMerchant.id(), "ONLINE", "두 번째 온라인")
        );

        assertThat(firstStore.code()).isEqualTo(secondStore.code());
        assertThatThrownBy(() -> merchantApplication.getStore(secondMerchant.id(), firstStore.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);
        assertThat(merchantApplication.getStores(firstMerchant.id()))
                .extracting(StoreView::id)
                .containsExactly(firstStore.id());
        assertThat(merchantApplication.getStores(secondMerchant.id()))
                .extracting(StoreView::id)
                .containsExactly(secondStore.id());
    }
}
