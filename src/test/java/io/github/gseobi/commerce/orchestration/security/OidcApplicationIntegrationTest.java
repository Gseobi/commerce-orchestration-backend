package io.github.gseobi.commerce.orchestration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.gseobi.commerce.orchestration.auth.controller.AuthController;
import io.github.gseobi.commerce.orchestration.integration.TestcontainersIntegrationSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@ActiveProfiles("integration-test")
@SpringBootTest(properties = {"app.security.mode=oidc", "app.security.jwt.secret="})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class OidcApplicationIntegrationTest extends TestcontainersIntegrationSupport {
    private static final TestJwksServer FIXTURE = fixture();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApplicationContext context;

    private static TestJwksServer fixture() {
        try {
            return new TestJwksServer();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot start test JWKS", exception);
        }
    }

    @DynamicPropertySource
    static void oidcProperties(DynamicPropertyRegistry registry) {
        registry.add("app.security.oidc.issuer", FIXTURE::issuer);
        registry.add("app.security.oidc.audience", () -> "commerce-api");
        registry.add("app.security.oidc.jwk-set-uri", () -> FIXTURE.properties(
                java.time.Duration.ofMinutes(5), java.time.Duration.ofSeconds(1)).jwkSetUri());
    }

    @AfterAll
    static void closeFixture() {
        FIXTURE.close();
    }

    @Test
    void oidcBootContextHasNoDemoBeansOrDemoSigningSecretRequirement() {
        assertThat(context.getBeansOfType(JwtTokenProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(JwtAuthenticationFilter.class)).isEmpty();
        assertThat(context.getBeansOfType(AuthController.class)).isEmpty();
        assertThat(context.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class)).hasSize(1);
    }

    @Test
    void validTokenStillCannotAccessLegacyBusinessOrAdminApis() throws Exception {
        String token = FIXTURE.token(FIXTURE.claims().claim("roles", java.util.List.of("ROLE_ADMIN"))
                .claim("merchantId", 1).build(), FIXTURE.key, "at+jwt");
        for (String path : java.util.List.of("/api/orders/1", "/api/admin/notification-events/retry-due", "/actuator/env")) {
            assertThat(mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(mvc.perform(post("/api/auth/token").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void invalidIdAndMalformedSubjectTokensReturnSanitized401() throws Exception {
        for (String token : java.util.List.of("invalid", FIXTURE.tokenWithRawClaim("sub", 123),
                FIXTURE.token(FIXTURE.claims().build(), FIXTURE.key, "JWT"))) {
            var response = mvc.perform(get("/api/orders/1").header("Authorization", "Bearer " + token))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).doesNotContain(token, "Bearer", "claims");
        }
    }

    @Test
    void noAuthenticationSurvivesIntoNextRequest() throws Exception {
        assertThat(mvc.perform(get("/api/orders/1").header("Authorization", "Bearer " + FIXTURE.token()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/orders/1")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
