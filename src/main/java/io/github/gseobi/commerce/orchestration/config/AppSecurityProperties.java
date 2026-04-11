package io.github.gseobi.commerce.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record AppSecurityProperties(
        String secret,
        long accessTokenValiditySeconds,
        String issuer
) {
}
