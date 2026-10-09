package io.github.gseobi.commerce.orchestration.security;

import io.github.gseobi.commerce.orchestration.config.OidcProperties;
import io.github.gseobi.commerce.orchestration.config.SecurityModeProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties({SecurityModeProperties.class, OidcProperties.class})
public class SecurityConfig {

    public SecurityConfig(SecurityModeProperties properties, OidcProperties oidc, Environment environment) {
        String configuredMode = environment.getProperty("app.security.mode", "disabled");
        if (properties.mode() == null || !configuredMode.equalsIgnoreCase(properties.mode().name())) {
            throw new IllegalArgumentException("A single explicit authentication mode is required");
        }
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        boolean production = profiles.contains("prod") || profiles.contains("production");
        boolean test = profiles.contains("test") || profiles.contains("integration-test");
        if (properties.mode() == SecurityModeProperties.Mode.DEMO
                && (production || !(test || profiles.contains("local")))) {
            throw new IllegalArgumentException("Demo authentication requires a local/test profile, never production");
        }
        if (properties.mode() == SecurityModeProperties.Mode.OIDC) {
            oidc.validate(test, production);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "demo")
    public SecurityFilterChain demoSecurityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwt,
            RestAuthenticationEntryPoint entryPoint, RequestTraceFilter trace) throws Exception {
        common(http, entryPoint, trace);
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/token").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
    public SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http, JwtDecoder decoder,
            RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler denied, RequestTraceFilter trace)
            throws Exception {
        common(http, entryPoint, trace);
        // No tenant-scoped HTTP API is exposed in this delivery unit.
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(denied))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(denied)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(token ->
                                new JwtAuthenticationToken(token, List.of(), TrustedActorResolver.actorId(
                                        token.getClaimAsString("iss"), token.getSubject())))));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.mode", havingValue = "disabled", matchIfMissing = true)
    public SecurityFilterChain disabledSecurityFilterChain(HttpSecurity http,
            RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler denied, RequestTraceFilter trace)
            throws Exception {
        common(http, entryPoint, trace);
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(denied));
        return http.build();
    }

    private void common(HttpSecurity http, RestAuthenticationEntryPoint entryPoint, RequestTraceFilter trace)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .addFilterBefore(trace, UsernamePasswordAuthenticationFilter.class);
    }
}
