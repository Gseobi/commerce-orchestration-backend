package io.github.gseobi.commerce.orchestration.merchant.api;

import java.util.List;

public interface MerchantApplication {

    MerchantView registerMerchant(MerchantRegistrationCommand command);

    StoreView registerStore(StoreRegistrationCommand command);

    MerchantView getMerchant(Long merchantId);

    StoreView getStore(Long merchantId, Long storeId);

    List<StoreView> getStores(Long merchantId);
}
