package io.github.gseobi.commerce.orchestration.security;

import io.github.gseobi.commerce.orchestration.config.AppSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String ROLES_CLAIM = "roles";

    private final AppSecurityProperties appSecurityProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(AppSecurityProperties appSecurityProperties) {
        this.appSecurityProperties = appSecurityProperties;
        this.secretKey = Keys.hmacShaKeyFor(appSecurityProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(appSecurityProperties.accessTokenValiditySeconds());

        return Jwts.builder()
                .subject(username)
                .issuer(appSecurityProperties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(ROLES_CLAIM, roles)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
        return true;
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        List<String> roles = claims.get(ROLES_CLAIM, List.class);
        Collection<SimpleGrantedAuthority> authorities = roles == null
                ? List.of()
                : roles.stream().map(SimpleGrantedAuthority::new).toList();
        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }
}
