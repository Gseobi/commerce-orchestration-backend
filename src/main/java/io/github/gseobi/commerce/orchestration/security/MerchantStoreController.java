package io.github.gseobi.commerce.orchestration.security;

import io.github.gseobi.commerce.orchestration.common.api.ApiResponse;
import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantPermission;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
class MerchantStoreController {
    private final MerchantRequestAccessAdapter access;
    private final MerchantApplication merchants;

    @GetMapping("/api/merchants/{merchantId}/stores/{storeId}")
    ApiResponse<StoreView> getStore(@PathVariable Long merchantId, @PathVariable Long storeId) {
        var context = access.authorize(merchantId, MerchantPermission.READ);
        if (storeId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return ApiResponse.success(merchants.getStore(context.merchantId(), storeId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> invalidPathIdentifier() {
        return ResponseEntity.badRequest().body(
                ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage()));
    }
}
