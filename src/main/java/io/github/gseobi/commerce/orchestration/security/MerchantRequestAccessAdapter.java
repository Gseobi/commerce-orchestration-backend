package io.github.gseobi.commerce.orchestration.security;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantAccessApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantAccessContext;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
class MerchantRequestAccessAdapter {
    private final MerchantAccessApplication access;

    MerchantAccessContext authorize(Long merchantId, MerchantPermission permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Object issuer = jwt.getToken().getClaims().get("iss");
        Object subject = jwt.getToken().getClaims().get("sub");
        if (!(issuer instanceof String verifiedIssuer) || !(subject instanceof String verifiedSubject)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String actorId;
        try {
            actorId = TrustedActorResolver.actorId(verifiedIssuer, verifiedSubject);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!actorId.equals(jwt.getName())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return access.authorize(merchantId, actorId, permission);
    }
}
