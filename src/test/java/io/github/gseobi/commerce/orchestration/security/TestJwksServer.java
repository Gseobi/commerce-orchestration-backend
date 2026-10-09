package io.github.gseobi.commerce.orchestration.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.github.gseobi.commerce.orchestration.config.OidcProperties;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

final class TestJwksServer implements AutoCloseable {
    final HttpServer server;
    final ExecutorService executor;
    final AtomicInteger requests = new AtomicInteger();
    RSAKey key;
    volatile String body;
    volatile int status = 200;
    volatile long delayMillis;

    TestJwksServer() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("first").generate();
        body = new JWKSet(key.toPublicJWK()).toString();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // Timed-out clients are expected in fault tests.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/issuer";
    }

    OidcProperties properties(Duration ttl, Duration readTimeout) {
        return new OidcProperties(issuer(), "commerce-api",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                Duration.ZERO, Duration.ofSeconds(1), readTimeout, ttl);
    }

    JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer(issuer()).subject("actor-1").audience("commerce-api")
                .issueTime(Date.from(now.minusSeconds(600))).expirationTime(Date.from(now.plusSeconds(3600)));
    }

    String token(JWTClaimsSet claims, RSAKey signingKey, String type) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType(type)).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    String token() throws Exception {
        return token(claims().build(), key, "at+jwt");
    }

    String tokenWithRawClaim(String name, Object value) throws Exception {
        var claims = new java.util.HashMap<>(claims().build().toJSONObject());
        claims.put(name, value);
        String json = com.nimbusds.jose.util.JSONObjectUtils.toJSONString(claims)
                .replace("\uD800", "\\uD800");
        var jwt = new com.nimbusds.jose.JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("at+jwt")).keyID(key.getKeyID()).build(),
                new com.nimbusds.jose.Payload(json));
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
