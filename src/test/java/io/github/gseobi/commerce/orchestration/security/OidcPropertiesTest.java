package io.github.gseobi.commerce.orchestration.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gseobi.commerce.orchestration.config.OidcProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OidcPropertiesTest {
    private OidcProperties properties(String issuer, String audience, String jwks, Duration skew,
            Duration connect, Duration read, Duration ttl) {
        return new OidcProperties(issuer, audience, jwks, skew, connect, read, ttl);
    }

    @Test
    void acceptsHttpsAndOnlyTestLoopbackHttp() {
        var https = properties("https://idp.example/issuer", "api", "https://idp.example/jwks",
                Duration.ofSeconds(30), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofMinutes(5));
        assertThatCode(() -> https.validate(false, true)).doesNotThrowAnyException();
        var loopback = properties("http://127.0.0.1/issuer", "api", "http://localhost/jwks",
                Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(5));
        assertThatCode(() -> loopback.validate(true, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> loopback.validate(false, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loopback.validate(true, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedEndpointErrorsDoNotEchoConfigurationValues() {
        String endpoint = "https://user:fixture-password@bad host/jwks";
        var config = properties("https://idp.example", "api", endpoint,
                Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(5));
        assertThatThrownBy(() -> config.validate(false, true)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid OIDC endpoint syntax").hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://idp.example/jwks", "ftp://idp.example/jwks", "/relative",
            "https://user:pass@idp.example/jwks", "https://idp.example/jwks#fragment"})
    void rejectsUnsafeEndpoints(String uri) {
        var config = properties("https://idp.example", "api", uri,
                Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(5));
        assertThatThrownBy(() -> config.validate(true, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "jwks", "skew", "connect", "read", "ttl"})
    void rejectsMissingConfigurationOrUnboundedDurations(String invalid) {
        var config = properties(invalid.equals("issuer") ? null : "https://idp.example",
                invalid.equals("audience") ? " " : "api", invalid.equals("jwks") ? null : "https://idp.example/jwks",
                invalid.equals("skew") ? Duration.ofSeconds(61) : Duration.ZERO,
                invalid.equals("connect") ? Duration.ZERO : Duration.ofSeconds(1),
                invalid.equals("read") ? Duration.ofSeconds(11) : Duration.ofSeconds(1),
                invalid.equals("ttl") ? Duration.ofMinutes(16) : Duration.ofMinutes(5));
        assertThatThrownBy(() -> config.validate(false, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
