package io.github.gseobi.commerce.orchestration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.gseobi.commerce.orchestration.auth.controller.AuthController;
import io.github.gseobi.commerce.orchestration.integration.TestcontainersIntegrationSupport;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreView;
import io.github.gseobi.commerce.orchestration.merchant.entity.MerchantMembership;
import io.github.gseobi.commerce.orchestration.merchant.entity.MembershipRole;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantMembershipRepository;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Autowired
    private MerchantApplication merchants;
    @Autowired
    private MerchantMembershipRepository memberships;
    @Autowired
    private MerchantRepository merchantRepository;

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

    @ParameterizedTest
    @EnumSource(MembershipRole.class)
    void activeViewerAndOperatorCanReadOnlyTheirMerchantStore(MembershipRole role) throws Exception {
        StoreView store = store("FIRST");
        member(store, "actor-1", role);
        mvc.perform(get(path(store)).header("Authorization", "Bearer " + FIXTURE.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(store.id()))
                .andExpect(jsonPath("$.data.merchantId").value(store.merchantId()))
                .andExpect(jsonPath("$.data.code").value("STORE"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.recoveryPolicy").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.data.actorId").doesNotExist())
                .andExpect(jsonPath("$.data.version").doesNotExist());
    }

    @Test
    void missingMembershipAndUnknownMerchantHaveSameSanitizedForbiddenResponse() throws Exception {
        StoreView store = store("FIRST");
        String token = FIXTURE.token();
        String denied = mvc.perform(get(path(store)).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(get("/api/merchants/999999/stores/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertThat(unknown).isEqualTo(denied);
    }

    @Test
    void crossMerchantPathIsForbiddenAndForeignStoreIdIsNotFound() throws Exception {
        StoreView first = store("FIRST");
        StoreView second = store("SECOND");
        member(first, "actor-1", MembershipRole.OPERATOR);
        String token = FIXTURE.token();
        mvc.perform(get(path(second)).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/merchants/" + first.merchantId() + "/stores/" + second.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void nonexistentStoreIsNotFoundOnlyAfterMembershipAuthorization() throws Exception {
        StoreView store = store("FIRST");
        member(store, "actor-1", MembershipRole.VIEWER);
        mvc.perform(get("/api/merchants/" + store.merchantId() + "/stores/999999")
                        .header("Authorization", "Bearer " + FIXTURE.token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    void revocationIsEffectiveOnNextHttpRequestWithSameToken() throws Exception {
        StoreView store = store("FIRST");
        var membership = member(store, "actor-1", MembershipRole.OPERATOR);
        String token = FIXTURE.token();
        mvc.perform(get(path(store)).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        membership.revoke();
        memberships.saveAndFlush(membership);
        mvc.perform(get(path(store)).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void suspendedMerchantDeniesOtherwiseActiveMember() throws Exception {
        StoreView store = store("FIRST");
        member(store, "actor-1", MembershipRole.VIEWER);
        var merchant = merchantRepository.findById(store.merchantId()).orElseThrow();
        merchant.suspend();
        merchantRepository.saveAndFlush(merchant);
        mvc.perform(get(path(store)).header("Authorization", "Bearer " + FIXTURE.token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void tokenRolesAndRequestIdentitySelectorsCannotImpersonateMember() throws Exception {
        StoreView store = store("FIRST");
        member(store, "actor-1", MembershipRole.OPERATOR);
        String spoof = FIXTURE.token(FIXTURE.claims().subject("outsider")
                .claim("roles", java.util.List.of("ROLE_ADMIN", "OPERATOR"))
                .claim("scope", "merchant:read").claim("merchantId", store.merchantId())
                .claim("actorId", TrustedActorResolver.actorId(FIXTURE.issuer(), "actor-1")).build(),
                FIXTURE.key, "at+jwt");
        mvc.perform(get(path(store)).header("Authorization", "Bearer " + spoof)
                        .header("X-Actor-Id", TrustedActorResolver.actorId(FIXTURE.issuer(), "actor-1"))
                        .header("X-Merchant-Id", store.merchantId())
                        .param("actorId", "actor-1").param("merchantId", store.merchantId().toString())
                        .contentType("application/json").content("{\"actorId\":\"actor-1\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void tokenAndTenantContextDoNotSurviveNextRequest() throws Exception {
        StoreView first = store("FIRST");
        StoreView second = store("SECOND");
        member(first, "actor-1", MembershipRole.VIEWER);
        member(second, "actor-2", MembershipRole.OPERATOR);
        String firstToken = FIXTURE.token();
        String secondToken = FIXTURE.token(FIXTURE.claims().subject("actor-2").build(), FIXTURE.key, "at+jwt");
        mvc.perform(get(path(first)).header("Authorization", "Bearer " + firstToken)).andExpect(status().isOk());
        mvc.perform(get(path(first)).header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(second)).header("Authorization", "Bearer " + secondToken)).andExpect(status().isOk());
        mvc.perform(get(path(second))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(first)).header("Authorization", "Bearer " + firstToken)).andExpect(status().isOk());
    }

    @Test
    void missingAndInvalidTokenCannotReachStoreEndpoint() throws Exception {
        StoreView store = store("FIRST");
        member(store, "actor-1", MembershipRole.OPERATOR);
        mvc.perform(get(path(store))).andExpect(status().isUnauthorized());
        for (String token : java.util.List.of("invalid", FIXTURE.tokenWithRawClaim("sub", 123),
                FIXTURE.token(FIXTURE.claims().build(), FIXTURE.key, "JWT"),
                FIXTURE.token(FIXTURE.claims().expirationTime(java.util.Date.from(java.time.Instant.now().minusSeconds(120)))
                        .build(), FIXTURE.key, "at+jwt"),
                FIXTURE.token(FIXTURE.claims().issuer("https://other.example").build(), FIXTURE.key, "at+jwt"))) {
            mvc.perform(get(path(store)).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    @Test
    void unimplementedMerchantRoutesAndWriteMethodsStayClosedEvenForOperator() throws Exception {
        StoreView store = store("FIRST");
        member(store, "actor-1", MembershipRole.OPERATOR);
        String token = FIXTURE.token();
        mvc.perform(post(path(store)).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        for (var method : java.util.List.of(org.springframework.http.HttpMethod.HEAD,
                org.springframework.http.HttpMethod.OPTIONS, org.springframework.http.HttpMethod.PUT,
                org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.DELETE)) {
            mvc.perform(request(method, path(store)).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        for (String path : java.util.List.of("/api/merchants", "/api/merchants/" + store.merchantId() + "/stores",
                path(store) + "/extra")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number", "9223372036854775808"})
    void invalidStoreIdentifiersReturnSanitizedBadRequest(String storeId) throws Exception {
        StoreView store = store("FIRST");
        member(store, "actor-1", MembershipRole.VIEWER);
        mvc.perform(get("/api/merchants/" + store.merchantId() + "/stores/" + storeId)
                        .header("Authorization", "Bearer " + FIXTURE.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private StoreView store(String code) {
        Long merchantId = merchants.registerMerchant(new MerchantRegistrationCommand(code, code, "Asia/Seoul")).id();
        return merchants.registerStore(new StoreRegistrationCommand(merchantId, "STORE", code + " store"));
    }

    private MerchantMembership member(StoreView store, String subject, MembershipRole role) {
        return memberships.saveAndFlush(new MerchantMembership(store.merchantId(),
                TrustedActorResolver.actorId(FIXTURE.issuer(), subject), role));
    }

    private String path(StoreView store) {
        return "/api/merchants/" + store.merchantId() + "/stores/" + store.id();
    }
}
