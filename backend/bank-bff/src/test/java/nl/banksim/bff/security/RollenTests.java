package nl.banksim.bff.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class RollenTests {

    @Test
    void realmRollenUitHetIdTokenWordenRollen() {
        var idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "1", "realm_access", Map.of("roles", List.of("admin", "offline_access"))));
        var authorities = new Rollen().mapAuthorities(List.of(new OidcUserAuthority(idToken)));
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_admin", "ROLE_offline_access", "OIDC_USER");
    }

    @Test
    void zonderRollenBlijftHetBij() {
        var idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "1"));
        assertThat(new Rollen().mapAuthorities(List.of(new OidcUserAuthority(idToken)))).hasSize(1);
    }

    @Test
    void beheerderNaarAdminKlantNaarStart() {
        assertThat(StartpaginaNaLogin.startpagina(new TestingAuthenticationToken("b", null, "ROLE_admin"))).isEqualTo("/admin");
        assertThat(StartpaginaNaLogin.startpagina(new TestingAuthenticationToken("k", null, "ROLE_klant"))).isEqualTo("/");
    }
}
