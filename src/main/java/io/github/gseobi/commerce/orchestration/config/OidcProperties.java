package io.github.gseobi.commerce.orchestration.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.security.oidc")
public record OidcProperties(
        String issuer, String audience, String jwkSetUri,
        @DefaultValue("30s") Duration clockSkew,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("5m") Duration cacheTtl
) {
    public void validate(boolean testProfile, boolean productionProfile) {
        validateEndpoint(issuer, testProfile && !productionProfile);
        validateEndpoint(jwkSetUri, testProfile && !productionProfile);
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("OIDC audience is required");
        }
        bounded(clockSkew, Duration.ZERO, Duration.ofSeconds(60));
        bounded(connectTimeout, Duration.ofMillis(1), Duration.ofSeconds(10));
        bounded(readTimeout, Duration.ofMillis(1), Duration.ofSeconds(10));
        bounded(cacheTtl, Duration.ofMillis(1), Duration.ofMinutes(15));
    }

    private static void bounded(Duration value, Duration min, Duration max) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException("OIDC duration is outside the permitted range");
        }
    }

    private static void validateEndpoint(String value, boolean allowTestHttp) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OIDC endpoint is required");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid OIDC endpoint syntax");
        }
        boolean https = "https".equals(uri.getScheme());
        boolean testHttp = allowTestHttp && "http".equals(uri.getScheme())
                && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || !(https || testHttp)) {
            throw new IllegalArgumentException("OIDC endpoints must use HTTPS (test loopback excepted)");
        }
    }
}
