package io.github.gseobi.commerce.orchestration.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TrustedActorResolver {
    private TrustedActorResolver() {
    }

    public static String actorId(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Verified issuer and subject are required");
        }
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(issuer)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(subject)) {
            throw new IllegalArgumentException("Identity must have a lossless UTF-8 representation");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("oidc-v1".getBytes(StandardCharsets.US_ASCII));
            append(digest, issuer);
            append(digest, subject);
            return "oidc:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
