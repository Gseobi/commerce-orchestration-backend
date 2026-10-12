package io.github.gseobi.commerce.orchestration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantAccessApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantAccessContext;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantPermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class MerchantRequestAccessAdapterTest {
    private final MerchantAccessApplication access = mock(MerchantAccessApplication.class);
    private final MerchantRequestAccessAdapter adapter = new MerchantRequestAccessAdapter(access);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatesCanonicalActorAndRequestedPermissionWithoutUsingRoleClaims() {
        var jwt = token("issuer", "subject");
        String actorId = TrustedActorResolver.actorId("issuer", "subject");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), actorId));
        var expected = new MerchantAccessContext(7L, actorId, Set.of(MerchantPermission.READ));
        when(access.authorize(7L, actorId, MerchantPermission.READ)).thenReturn(expected);

        assertThat(adapter.authorize(7L, MerchantPermission.READ)).isSameAs(expected);
        verify(access).authorize(7L, actorId, MerchantPermission.READ);
    }

    @Test
    void missingAuthenticationNeverQueriesMembership() {
        assertForbidden();
    }

    @Test
    void demoPrincipalNeverQueriesMembership() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("oidc:spoof", "", List.of()));
        assertForbidden();
    }

    @Test
    void unauthenticatedJwtNeverQueriesMembership() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token("issuer", "subject")));
        assertForbidden();
    }

    @Test
    void nonCanonicalPrincipalNeverQueriesMembership() {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token("issuer", "subject"), List.of(), "subject"));
        assertForbidden();
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "sub"})
    void nonStringClaimsNeverQueryMembership(String claim) {
        var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256")
                .claim("iss", "issuer").claim("sub", "subject").claim(claim, 123).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), "spoof"));
        assertForbidden();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\uD800"})
    void invalidSubjectNeverQueriesMembership(String subject) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token("issuer", subject), List.of(), "spoof"));
        assertForbidden();
    }

    @Test
    void membershipDenialIsNotConvertedIntoPermission() {
        String actorId = TrustedActorResolver.actorId("issuer", "subject");
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token("issuer", "subject"), List.of(), actorId));
        when(access.authorize(7L, actorId, MerchantPermission.READ)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> adapter.authorize(7L, MerchantPermission.READ))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    private Jwt token(String issuer, String subject) {
        return Jwt.withTokenValue("fixture").header("alg", "RS256").claim("iss", issuer).claim("sub", subject)
                .claim("roles", List.of("ROLE_ADMIN")).claim("merchantId", 999).build();
    }

    private void assertForbidden() {
        assertThatThrownBy(() -> adapter.authorize(7L, MerchantPermission.READ))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(access);
    }
}
