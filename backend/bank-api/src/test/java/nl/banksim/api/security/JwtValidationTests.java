package nl.banksim.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtValidationTests {

    private static final String ISSUER = "https://auth.localtest.me/realms/banksim";

    private final BanksimSecurityProperties properties = new BanksimSecurityProperties(
            ISSUER, URI.create("https://keycloak.banksim.svc:8443/certs"), "bank-api", null);

    @Test
    void geldigTokenWordtGeaccepteerd() {
        assertThat(SecurityConfig.jwtValidator(properties).validate(token(ISSUER, List.of("bank-api"))).hasErrors())
                .isFalse();
    }

    @Test
    void verkeerdeAudienceWordtGeweigerd() {
        assertThat(SecurityConfig.jwtValidator(properties).validate(token(ISSUER, List.of("account"))).hasErrors())
                .isTrue();
    }

    @Test
    void verkeerdeIssuerWordtGeweigerd() {
        assertThat(SecurityConfig.jwtValidator(properties)
                .validate(token("https://evil.example/realms/banksim", List.of("bank-api"))).hasErrors())
                .isTrue();
    }

    @Test
    void realmRollenWordenAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("realm_access", Map.of("roles", List.of("klant", "offline_access")))
                .build();
        assertThat(SecurityConfig.realmRoles(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_klant", "ROLE_offline_access");
    }

    private static Jwt token(String issuer, List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("t").header("alg", "RS256")
                .issuer(issuer).audience(audience).subject("user")
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .build();
    }
}
