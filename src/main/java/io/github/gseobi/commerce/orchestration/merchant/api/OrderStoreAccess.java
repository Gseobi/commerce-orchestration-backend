package io.github.gseobi.commerce.orchestration.merchant.api;

public interface OrderStoreAccess {

    /** Requires a writable caller transaction. Locks active merchant and store through order creation. */
    OrderStoreSnapshot lockActiveStore(long merchantId, long storeId);

    record OrderStoreSnapshot(String timezone, String recoveryPolicy) {
    }
}
