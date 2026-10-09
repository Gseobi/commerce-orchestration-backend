package io.github.gseobi.commerce.orchestration.security;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.Collection;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class StrictClaimShapeJwtDecoder implements JwtDecoder {
    private final JwtDecoder delegate;

    StrictClaimShapeJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) {
        try {
            // Check raw types before framework claim coercion; only the delegate establishes trust.
            Map<String, Object> claims = SignedJWT.parse(token).getPayload().toJSONObject();
            if (claims == null || !validIdentity(claims.get("sub"))
                    || !validIdentity(claims.get("iss")) || !(claims.get("exp") instanceof Number)
                    || (claims.containsKey("nbf") && !(claims.get("nbf") instanceof Number))
                    || !validAudience(claims.get("aud"))) {
                throw new BadJwtException("Invalid access token");
            }
            return delegate.decode(token);
        } catch (ParseException | IllegalArgumentException exception) {
            throw new BadJwtException("Invalid access token");
        }
    }

    private boolean validAudience(Object audience) {
        return audience instanceof String
                || (audience instanceof Collection<?> values && !values.isEmpty()
                    && values.stream().allMatch(String.class::isInstance));
    }

    private boolean validIdentity(Object value) {
        return value instanceof String text && !text.isBlank()
                && StandardCharsets.UTF_8.newEncoder().canEncode(text);
    }
}
