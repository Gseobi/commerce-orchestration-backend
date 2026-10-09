package io.github.gseobi.commerce.orchestration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TrustedActorResolverTest {
    @Test
    void stableBoundedIdentityPreservesIssuerAndSubjectBoundaries() {
        String actor = TrustedActorResolver.actorId("https://issuer", "abc");
        assertThat(actor).matches("oidc:[0-9a-f]{64}");
        assertThat(actor).isEqualTo(TrustedActorResolver.actorId("https://issuer", "abc"));
        assertThat(actor).isNotEqualTo(TrustedActorResolver.actorId("https://other", "abc"));
        assertThat(actor).isNotEqualTo(TrustedActorResolver.actorId("https://issuer", "ABC"));
        assertThat(TrustedActorResolver.actorId("ab", "c"))
                .isNotEqualTo(TrustedActorResolver.actorId("a", "bc"));
        assertThat(TrustedActorResolver.actorId("https://issuer", "한글")).hasSize(69);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\uD800"})
    void rejectsMissingIdentity(String value) {
        assertThatThrownBy(() -> TrustedActorResolver.actorId(value, "subject"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedActorResolver.actorId("issuer", value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
