package io.github.gseobi.commerce.orchestration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gseobi.commerce.orchestration.auth.controller.AuthController;
import io.github.gseobi.commerce.orchestration.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SecurityModeTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(Bootstrap.class);

    @Test
    void missingModeDisablesAuthenticationAndDemoBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(org.springframework.security.web.SecurityFilterChain.class)
                    .doesNotHaveBean(JwtTokenProvider.class).doesNotHaveBean(AuthController.class)
                    .doesNotHaveBean(JwtAuthenticationFilter.class);
            var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                    .apply(springSecurity()).build();
            assertThat(mvc.perform(post("/api/auth/token")).andReturn().getResponse().getStatus()).isEqualTo(401);
            assertThat(mvc.perform(get("/api/orders/1")).andReturn().getResponse().getStatus()).isEqualTo(401);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production", "test,prod", "default"})
    void demoOutsideLocalTestOrCombinedWithProductionFailsStartup(String profile) {
        runner.withPropertyValues("app.security.mode=demo", "spring.profiles.active=" + profile)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "demo,oidc", " oidc "})
    void unknownOrMultipleModesFailStartup(String mode) {
        runner.withPropertyValues("app.security.mode=" + mode)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void demoOptInPreservesDemoBeansAndSingleFilterChain() {
        runner.withPropertyValues("app.security.mode=demo", "spring.profiles.active=local",
                "app.security.jwt.secret=local-fixture-signing-key-with-32-bytes",
                "app.security.jwt.issuer=demo", "app.security.jwt.access-token-validity-seconds=3600")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtTokenProvider.class)
                        .hasSingleBean(AuthController.class)
                        .hasSingleBean(org.springframework.security.web.SecurityFilterChain.class));
    }

    @Test
    void incompleteOidcConfigurationFailsStartup() {
        runner.withPropertyValues("app.security.mode=oidc")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void oidcAcceptsVerifiedIdentityButBlocksLegacyApiAndRoleSpoofing() throws Exception {
        try (var fixture = new TestJwksServer()) {
            String token = fixture.token(fixture.claims().claim("roles", java.util.List.of("ROLE_ADMIN"))
                    .claim("merchantId", 1).build(), fixture.key, "at+jwt");
            runner.withPropertyValues("app.security.mode=oidc", "spring.profiles.active=test",
                    "app.security.oidc.issuer=" + fixture.issuer(),
                    "app.security.oidc.audience=commerce-api",
                    "app.security.oidc.jwk-set-uri=" + fixture.properties(java.time.Duration.ofMinutes(5),
                            java.time.Duration.ofSeconds(1)).jwkSetUri())
                    .run(context -> {
                        assertThat(context).hasNotFailed().doesNotHaveBean(JwtTokenProvider.class)
                                .doesNotHaveBean(AuthController.class).doesNotHaveBean(JwtAuthenticationFilter.class)
                                .hasSingleBean(org.springframework.security.oauth2.jwt.JwtDecoder.class)
                                .hasSingleBean(org.springframework.security.web.SecurityFilterChain.class);
                        var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                                .apply(springSecurity()).build();
                        for (String path : java.util.List.of("/api/orders/1", "/api/admin/reprocess", "/actuator/env")) {
                            var response = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                                    .andReturn().getResponse();
                            assertThat(response.getStatus()).isEqualTo(403);
                            assertThat(response.getContentAsString()).doesNotContain(token, "ROLE_ADMIN");
                        }
                        assertThat(mvc.perform(post("/api/auth/token").header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getStatus()).isEqualTo(403);
                        var invalid = mvc.perform(get("/api/orders/1").header("Authorization", "Bearer invalid"))
                                .andReturn().getResponse();
                        assertThat(invalid.getStatus()).isEqualTo(401);
                        assertThat(invalid.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
                        assertThat(invalid.getContentAsString()).doesNotContain("invalid_token", "Bearer");
                        for (String malformed : java.util.List.of(fixture.tokenWithRawClaim("sub", 123),
                                fixture.token(fixture.claims().build(), fixture.key, "JWT"),
                                fixture.token(fixture.claims().audience("other-api").build(), fixture.key, "at+jwt"))) {
                            assertThat(mvc.perform(get("/api/orders/1").header("Authorization", "Bearer " + malformed))
                                    .andReturn().getResponse().getStatus()).isEqualTo(401);
                        }
                        assertThat(mvc.perform(get("/api/orders/1")).andReturn().getResponse().getStatus())
                                .isEqualTo(401);
                    });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableConfigurationProperties(AppSecurityProperties.class)
    @Import({SecurityConfig.class, OidcSecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class,
            AuthController.class, RequestTraceFilter.class, RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class})
    static class Bootstrap {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
