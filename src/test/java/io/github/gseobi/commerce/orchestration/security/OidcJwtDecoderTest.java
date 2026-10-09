package io.github.gseobi.commerce.orchestration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class OidcJwtDecoderTest {
    private TestJwksServer fixture;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new TestJwksServer();
        decoder = decoder(Duration.ofMinutes(5), Duration.ofSeconds(1));
    }

    private JwtDecoder decoder(Duration ttl, Duration timeout) {
        return new OidcSecurityConfig().oidcJwtDecoder(fixture.properties(ttl, timeout));
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void acceptsApiAccessTokenAndCachesPublicKeys() throws Exception {
        assertThat(decoder.decode(fixture.token()).getSubject()).isEqualTo("actor-1");
        decoder.decode(fixture.token());
        assertThat(fixture.requests).hasValue(1);
    }

    @Test
    void appliesConfiguredClockSkewAndAcceptsExplicitAccessTokenMediaType() throws Exception {
        var base = fixture.properties(Duration.ofMinutes(5), Duration.ofSeconds(1));
        var properties = new io.github.gseobi.commerce.orchestration.config.OidcProperties(base.issuer(),
                base.audience(), base.jwkSetUri(), Duration.ofSeconds(30), base.connectTimeout(),
                base.readTimeout(), base.cacheTtl());
        decoder = new OidcSecurityConfig().oidcJwtDecoder(properties);
        String withinSkew = fixture.token(fixture.claims()
                .expirationTime(Date.from(Instant.now().minusSeconds(5))).build(), fixture.key, "application/at+jwt");
        assertThat(decoder.decode(withinSkew).getSubject()).isEqualTo("actor-1");
        String outsideSkew = fixture.token(fixture.claims()
                .expirationTime(Date.from(Instant.now().minusSeconds(120))).build(), fixture.key, "at+jwt");
        assertThatThrownBy(() -> decoder.decode(outsideSkew)).isInstanceOf(JwtException.class);
    }

    @Test
    void ignoresTokenSuppliedJwkUrl() throws Exception {
        var untrustedRequests = new java.util.concurrent.atomic.AtomicInteger();
        fixture.server.createContext("/untrusted", exchange -> {
            untrustedRequests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        var jwt = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(
                com.nimbusds.jose.JWSAlgorithm.RS256).type(new com.nimbusds.jose.JOSEObjectType("at+jwt"))
                .keyID(fixture.key.getKeyID()).jwkURL(java.net.URI.create(fixture.issuer().replace("/issuer", "/untrusted")))
                .build(), fixture.claims().build());
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(fixture.key));
        decoder.decode(jwt.serialize());
        assertThat(untrustedRequests).hasValue(0);
        assertThat(fixture.requests).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "noAudience", "noIssuer", "expired", "noExpiry",
            "futureNbf", "noSubject", "blankSubject", "numericSubject", "idToken"})
    void rejectsInvalidClaimsAndIdTokens(String scenario) throws Exception {
        JWTClaimsSet.Builder claims = fixture.claims();
        String type = "at+jwt";
        switch (scenario) {
            case "issuer" -> claims.issuer(fixture.issuer() + "/");
            case "audience" -> claims.audience("other-api");
            case "noAudience" -> claims.audience((String) null);
            case "noIssuer" -> claims.issuer(null);
            case "expired" -> claims.expirationTime(Date.from(Instant.now().minusSeconds(120)));
            case "noExpiry" -> claims.expirationTime(null);
            case "futureNbf" -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(120)));
            case "noSubject" -> claims.subject(null);
            case "blankSubject" -> claims.subject(" ");
            case "numericSubject" -> claims.claim("sub", 123);
            case "idToken" -> type = "JWT";
            default -> throw new IllegalArgumentException("Unknown scenario");
        }
        String token = scenario.equals("numericSubject") ? fixture.tokenWithRawClaim("sub", 123)
                : fixture.token(claims.build(), fixture.key, type);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnsignedAndMalformedTokens() {
        String unsigned = new PlainJWT(fixture.claims().build()).serialize();
        assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("invalid")).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsHmacDemoAlgorithm() throws Exception {
        var token = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(
                com.nimbusds.jose.JWSAlgorithm.HS256).type(new com.nimbusds.jose.JOSEObjectType("at+jwt")).build(),
                fixture.claims().build());
        token.sign(new com.nimbusds.jose.crypto.MACSigner(new byte[32]));
        assertThatThrownBy(() -> decoder.decode(token.serialize())).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesRedirectedJwksAndDoesNotFollowLocation() throws Exception {
        fixture.server.removeContext("/jwks");
        fixture.server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Location", fixture.issuer() + "/untrusted");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        var followed = new java.util.concurrent.atomic.AtomicInteger();
        fixture.server.createContext("/issuer/untrusted", exchange -> {
            followed.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        assertThatThrownBy(() -> decoder.decode(fixture.token())).isInstanceOf(JwtException.class);
        assertThat(followed).hasValue(0);
    }

    @Test
    void rejectsCoercibleIssuerAudienceAndTimeClaims() throws Exception {
        for (var entry : java.util.Map.<String, Object>of("iss", 123, "aud", java.util.List.of(123),
                "exp", "2099999999", "nbf", "1", "sub", "\uD800").entrySet()) {
            String token = fixture.tokenWithRawClaim(entry.getKey(), entry.getValue());
            assertThatThrownBy(() -> decoder.decode(token)).as(entry.getKey()).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void rejectsForgedSignatureAndUnknownKid() throws Exception {
        var forged = new RSAKeyGenerator(2048).keyID("first").generate();
        String token = fixture.token(fixture.claims().build(), forged, "at+jwt");
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        var unknown = new RSAKeyGenerator(2048).keyID("unknown").generate();
        String other = fixture.token(fixture.claims().build(), unknown, "at+jwt");
        assertThatThrownBy(() -> decoder.decode(other)).isInstanceOf(JwtException.class);
    }

    @Test
    void refreshesJwksOnKeyRotation() throws Exception {
        decoder.decode(fixture.token());
        fixture.key = new RSAKeyGenerator(2048).keyID("second").generate();
        fixture.body = new JWKSet(fixture.key.toPublicJWK()).toString();
        assertThat(decoder.decode(fixture.token()).getSubject()).isEqualTo("actor-1");
        assertThat(fixture.requests.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void cachedKeyWorksDuringOutageButExpiredCacheFailsClosed() throws Exception {
        decoder = decoder(Duration.ofMillis(50), Duration.ofSeconds(1));
        decoder.decode(fixture.token());
        fixture.status = 503;
        Thread.sleep(100);
        assertThatThrownBy(() -> decoder.decode(fixture.token())).isInstanceOf(JwtException.class);
    }

    @Test
    void usesFreshCachedKeyDuringOutage() throws Exception {
        decoder.decode(fixture.token());
        fixture.status = 503;
        decoder.decode(fixture.token());
        assertThat(fixture.requests).hasValue(1);
    }

    @Test
    void rejectsMalformedJwksAndReadTimeout() throws Exception {
        fixture.body = "not-json";
        assertThatThrownBy(() -> decoder.decode(fixture.token())).isInstanceOf(JwtException.class);
        fixture.body = new JWKSet(fixture.key.toPublicJWK()).toString();
        fixture.delayMillis = 400;
        decoder = decoder(Duration.ofMinutes(5), Duration.ofMillis(50));
        assertThatThrownBy(() -> decoder.decode(fixture.token())).isInstanceOf(JwtException.class);
    }
}
