package io.github.gseobi.commerce.orchestration.security;

import io.github.gseobi.commerce.orchestration.config.OidcProperties;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class OidcTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final OidcProperties properties;

    OidcTokenValidator(OidcProperties properties) {
        this.properties = properties;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object subject = jwt.getClaims().get("sub");
        if (!properties.issuer().equals(jwt.getClaimAsString("iss"))
                || jwt.getAudience() == null || !jwt.getAudience().contains(properties.audience())
                || jwt.getExpiresAt() == null
                || !(subject instanceof String value) || value.isBlank()
                || !("at+jwt".equals(jwt.getHeaders().get("typ"))
                    || "application/at+jwt".equals(jwt.getHeaders().get("typ")))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
