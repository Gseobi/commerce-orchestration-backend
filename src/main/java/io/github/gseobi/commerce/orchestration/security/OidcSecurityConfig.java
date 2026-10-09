package io.github.gseobi.commerce.orchestration.security;

import io.github.gseobi.commerce.orchestration.config.OidcProperties;
import java.net.http.HttpClient;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class OidcSecurityConfig {
    @Bean
    public JwtDecoder oidcJwtDecoder(OidcProperties properties) {
        // No discovery or token-supplied URL; redirects cannot change the trusted JWKS destination.
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.readTimeout());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256).validateType(false)
                .restOperations(new RestTemplate(factory))
                .cache(new ExpiringJwksCache(properties.cacheTtl())).build();
        decoder.setClaimSetConverter(claims -> {
            if (!(claims.get("sub") instanceof String)) {
                throw new BadJwtException("Invalid access token");
            }
            return MappedJwtClaimSetConverter.withDefaults(Map.of()).convert(claims);
        });
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(properties.clockSkew()), new OidcTokenValidator(properties)));
        return new StrictClaimShapeJwtDecoder(decoder);
    }
}
