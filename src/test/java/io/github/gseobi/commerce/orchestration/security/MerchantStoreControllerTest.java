package io.github.gseobi.commerce.orchestration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantAccessContext;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantPermission;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreView;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MerchantStoreControllerTest {
    private final MerchantRequestAccessAdapter access = mock(MerchantRequestAccessAdapter.class);
    private final MerchantApplication merchants = mock(MerchantApplication.class);
    private final MerchantStoreController controller = new MerchantStoreController(access, merchants);

    @Test
    void authorizesBeforeScopedLookupAndReturnsOnlyStoreView() {
        var store = new StoreView(2L, 1L, "STORE", "Store", "ACTIVE", "Asia/Seoul", "MANUAL_REVIEW");
        when(access.authorize(1L, MerchantPermission.READ)).thenReturn(context());
        when(merchants.getStore(1L, 2L)).thenReturn(store);

        assertThat(controller.getStore(1L, 2L).data()).isSameAs(store);
        var order = inOrder(access, merchants);
        order.verify(access).authorize(1L, MerchantPermission.READ);
        order.verify(merchants).getStore(1L, 2L);
    }

    @Test
    void deniedMembershipNeverLooksUpStore() {
        when(access.authorize(1L, MerchantPermission.READ)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> controller.getStore(1L, 2L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(merchants);
    }

    @Test
    void nonPositiveStoreIdIsBadRequestAfterAuthorization() {
        when(access.authorize(1L, MerchantPermission.READ)).thenReturn(context());
        assertThatThrownBy(() -> controller.getStore(1L, 0L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoInteractions(merchants);
    }

    @Test
    void missingStoreRemainsNotFound() {
        when(access.authorize(1L, MerchantPermission.READ)).thenReturn(context());
        when(merchants.getStore(1L, 2L)).thenThrow(new BusinessException(ErrorCode.STORE_NOT_FOUND));
        assertThatThrownBy(() -> controller.getStore(1L, 2L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }

    private MerchantAccessContext context() {
        return new MerchantAccessContext(1L, "oidc:fixture", Set.of(MerchantPermission.READ));
    }
}
